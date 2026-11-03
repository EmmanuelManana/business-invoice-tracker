package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicetracker.api.support.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

class InvoiceIT extends IntegrationTest {

    private final String today = today().toString();
    private final String inThirtyDays = today().plusDays(30).toString();

    @Test
    void createComputesTotalsOnTheServerAndNumbersSequentially() {
        Session session = register();
        String customer = createCustomer(session, "Globex");

        Map<String, Object> body = new HashMap<>(invoiceBody(customer, today, inThirtyDays));
        body.put("total", "1.00"); // client-supplied totals must be ignored
        var first = post("/api/v1/invoices", session.token(), body);
        JsonNode second = createInvoice(session, customer, today, inThirtyDays);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getHeaders().getLocation()).isNotNull();
        assertThat(first.getHeaders().getETag()).isEqualTo("\"0\"");
        JsonNode invoice = first.getBody();
        assertThat(invoice.get("status").asText()).isEqualTo("DRAFT");
        assertThat(invoice.get("subtotal").decimalValue()).isEqualByComparingTo("3250.00");
        assertThat(invoice.get("vatTotal").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(invoice.get("total").decimalValue()).isEqualByComparingTo("3700.00");
        int year = today().getYear();
        assertThat(invoice.get("number").asText()).isEqualTo("INV-%d-0001".formatted(year));
        assertThat(second.get("number").asText()).isEqualTo("INV-%d-0002".formatted(year));
    }

    @Test
    void dueDateDefaultsFromPaymentTerms() {
        Session session = register();
        String customer = createCustomer(session, "Terms Co");
        var response = post(
                "/api/v1/invoices",
                session.token(),
                Map.of(
                        "customerId",
                        customer,
                        "lineItems",
                        List.of(Map.of("description", "Work", "quantity", 1, "unitPrice", "100.00"))));

        assertThat(response.getBody().get("issueDate").asText()).isEqualTo(today);
        assertThat(response.getBody().get("dueDate").asText()).isEqualTo(inThirtyDays);
        // VAT defaults to 15% when not specified
        assertThat(response.getBody().get("vatTotal").decimalValue()).isEqualByComparingTo("15.00");
    }

    @Test
    void draftUpdatesUseOptimisticConcurrency() {
        Session session = register();
        String customer = createCustomer(session, "Initech");
        JsonNode invoice = createInvoice(session, customer, today, inThirtyDays);
        String path = "/api/v1/invoices/" + invoice.get("id").asText();
        var update = invoiceBody(customer, today, today().plusDays(45).toString());

        var missing = call(HttpMethod.PUT, path, session.token(), update);
        assertThat(missing.getStatusCode().value()).isEqualTo(428);

        var ok = call(HttpMethod.PUT, path, session.token(), update, ifMatch("\"0\""));
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getHeaders().getETag()).isEqualTo("\"1\"");

        var stale = call(HttpMethod.PUT, path, session.token(), update, ifMatch("\"0\""));
        assertThat(stale.getStatusCode().value()).isEqualTo(412);
        assertThat(stale.getBody().get("code").asText()).isEqualTo("version_mismatch");
    }

    @Test
    void lifecycleEnforcesTransitionsAndRecordsPaymentsIdempotently() {
        Session session = register();
        String customer = createCustomer(session, "Umbrella");
        String id =
                createInvoice(session, customer, today, inThirtyDays).get("id").asText();
        String path = "/api/v1/invoices/" + id;

        assertThat(pay(session, id, "100.00", "early").getStatusCode().value())
                .as("draft does not accept payments")
                .isEqualTo(422);

        var sent = post(path + "/send", session.token(), null);
        assertThat(sent.getBody().get("status").asText()).isEqualTo("SENT");

        var sendAgain = post(path + "/send", session.token(), null);
        assertThat(sendAgain.getStatusCode().value()).isEqualTo(409);
        assertThat(sendAgain.getBody().get("code").asText()).isEqualTo("invalid_state_transition");
        assertThat(sendAgain.getBody().get("currentStatus").asText()).isEqualTo("SENT");

        var editSent = call(
                HttpMethod.PUT, path, session.token(), invoiceBody(customer, today, inThirtyDays), ifMatch("\"1\""));
        assertThat(editSent.getStatusCode().value()).isIn(412, 422);

        var partial = pay(session, id, "1000.00", "pay-1");
        assertThat(partial.getStatusCode().value()).isEqualTo(201);
        assertThat(partial.getBody().at("/invoice/status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(partial.getBody().at("/invoice/balanceDue").decimalValue()).isEqualByComparingTo("2700.00");

        var replay = pay(session, id, "1000.00", "pay-1");
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.getBody().at("/payment/id"))
                .isEqualTo(partial.getBody().at("/payment/id"));
        assertThat(replay.getBody().at("/invoice/payments").size()).isEqualTo(1);

        assertThat(pay(session, id, "5000.00", "pay-too-much")
                        .getBody()
                        .get("code")
                        .asText())
                .isEqualTo("overpayment");

        var full = pay(session, id, "2700.00", "pay-2");
        assertThat(full.getBody().at("/invoice/status").asText()).isEqualTo("PAID");

        var cancelPaid = post(path + "/cancel", session.token(), Map.of("reason", "too late"));
        assertThat(cancelPaid.getStatusCode().value()).isEqualTo(409);

        JsonNode activity = get(path + "/activity", session.token()).getBody();
        assertThat(activity.findValuesAsText("type")).contains("CREATED", "SENT", "PAYMENT_RECORDED", "PAID");
    }

    @Test
    void paymentRequiresIdempotencyKey() {
        Session session = register();
        String id = createInvoice(session, createCustomer(session, "Keyless"), today, inThirtyDays)
                .get("id")
                .asText();
        post("/api/v1/invoices/" + id + "/send", session.token(), null);

        var response = post(
                "/api/v1/invoices/" + id + "/payments",
                session.token(),
                Map.of("amount", "10.00", "paidOn", today, "method", "EFT"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void cancelOpenInvoice() {
        Session session = register();
        String id = createInvoice(session, createCustomer(session, "Cancel Co"), today, inThirtyDays)
                .get("id")
                .asText();
        post("/api/v1/invoices/" + id + "/send", session.token(), null);

        var cancelled = post("/api/v1/invoices/" + id + "/cancel", session.token(), Map.of("reason", "Duplicate"));

        assertThat(cancelled.getBody().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.getBody().get("cancellationReason").asText()).isEqualTo("Duplicate");
    }

    @Test
    void listSupportsFilteringSearchSortingAndPaging() {
        Session session = register();
        String globex = createCustomer(session, "Globex Corporation");
        String initech = createCustomer(session, "Initech");
        String a = createInvoice(session, globex, today, inThirtyDays).get("id").asText();
        createInvoice(session, globex, today().minusDays(40).toString(), inThirtyDays);
        createInvoice(session, initech, today, inThirtyDays);
        post("/api/v1/invoices/" + a + "/send", session.token(), null);

        assertThat(get("/api/v1/invoices?status=SENT", session.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isEqualTo(1);
        assertThat(get("/api/v1/invoices?customerId=" + globex, session.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isEqualTo(2);
        assertThat(get("/api/v1/invoices?search=initech", session.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isEqualTo(1);
        assertThat(get("/api/v1/invoices?from=" + today().minusDays(1), session.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isEqualTo(2);

        JsonNode paged = get("/api/v1/invoices?sort=customer&direction=asc&size=2&page=0", session.token())
                .getBody();
        assertThat(paged.get("items").size()).isEqualTo(2);
        assertThat(paged.get("totalPages").asInt()).isEqualTo(2);
        assertThat(paged.at("/items/0/customerName").asText()).isEqualTo("Globex Corporation");

        assertThat(get("/api/v1/invoices?sort=bogus", session.token())
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    @Test
    void customerWithInvoicesCannotBeDeleted() {
        Session session = register();
        String customer = createCustomer(session, "Sticky");
        createInvoice(session, customer, today, inThirtyDays);

        var response = call(HttpMethod.DELETE, "/api/v1/customers/" + customer, session.token(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().get("code").asText()).isEqualTo("customer_has_invoices");
    }

    @Test
    void invoiceCanBeDownloadedAsPdf() {
        Session session = register();
        String id = createInvoice(session, createCustomer(session, "Pdf Co"), today, inThirtyDays)
                .get("id")
                .asText();
        var headers = new HttpHeaders();
        headers.setBearerAuth(session.token());

        var response = http.exchange(
                "/api/v1/invoices/" + id + "/pdf", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(new String(response.getBody(), 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void dashboardSummarisesOutstandingAndPaid() {
        Session session = register();
        String customer = createCustomer(session, "Dash Co");
        String open =
                createInvoice(session, customer, today, inThirtyDays).get("id").asText();
        String paid =
                createInvoice(session, customer, today, inThirtyDays).get("id").asText();
        post("/api/v1/invoices/" + open + "/send", session.token(), null);
        post("/api/v1/invoices/" + paid + "/send", session.token(), null);
        pay(session, paid, "3700.00", "dash-paid");

        JsonNode summary = get("/api/v1/dashboard/summary", session.token()).getBody();

        assertThat(summary.get("currency").asText()).isEqualTo("ZAR");
        assertThat(summary.get("totalOutstanding").decimalValue()).isEqualByComparingTo("3700.00");
        assertThat(summary.get("paidThisMonth").decimalValue()).isEqualByComparingTo("3700.00");
        assertThat(summary.at("/countsByStatus/PAID").asLong()).isEqualTo(1);
        assertThat(summary.at("/countsByStatus/SENT").asLong()).isEqualTo(1);
        assertThat(summary.get("ageing").size()).isEqualTo(5);
        assertThat(summary.at("/ageing/0/amount").decimalValue()).isEqualByComparingTo("3700.00");
    }

    private static HttpHeaders ifMatch(String etag) {
        var headers = new HttpHeaders();
        headers.setIfMatch(etag);
        return headers;
    }
}
