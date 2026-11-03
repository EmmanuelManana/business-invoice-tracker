package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicetracker.api.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** A business must never be able to read or change another business's data. */
class TenantIsolationIT extends IntegrationTest {

    @Test
    void anotherBusinessCannotSeeOrTouchInvoicesOrCustomers() {
        Session alice = register();
        Session bob = register();
        String aliceCustomer = createCustomer(alice, "Alice Client");
        String aliceInvoice = createInvoice(
                        alice,
                        aliceCustomer,
                        today().toString(),
                        today().plusDays(30).toString())
                .get("id")
                .asText();

        // Reads: 404, so the resource's existence is not revealed.
        assertThat(get("/api/v1/invoices/" + aliceInvoice, bob.token())
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
        assertThat(get("/api/v1/customers/" + aliceCustomer, bob.token())
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
        assertThat(get("/api/v1/invoices/" + aliceInvoice + "/activity", bob.token())
                        .getStatusCode()
                        .value())
                .isEqualTo(404);

        // Lists are scoped to the caller.
        assertThat(get("/api/v1/invoices", bob.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isZero();
        assertThat(get("/api/v1/customers", bob.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isZero();

        // Writes: also 404.
        assertThat(post("/api/v1/invoices/" + aliceInvoice + "/send", bob.token(), null)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
        assertThat(pay(bob, aliceInvoice, "10.00", "k-1").getStatusCode().value())
                .isEqualTo(404);
        assertThat(call(HttpMethod.DELETE, "/api/v1/customers/" + aliceCustomer, bob.token(), null)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);

        // Bob cannot invoice Alice's customer.
        var crossTenantInvoice = post(
                "/api/v1/invoices",
                bob.token(),
                invoiceBody(
                        aliceCustomer, today().toString(), today().plusDays(7).toString()));
        assertThat(crossTenantInvoice.getStatusCode().value()).isEqualTo(404);

        // Alice's data is untouched.
        var aliceView = get("/api/v1/invoices/" + aliceInvoice, alice.token());
        assertThat(aliceView.getBody().get("status").asText()).isEqualTo("DRAFT");
        assertThat(get("/api/v1/business/profile", bob.token())
                        .getBody()
                        .get("id")
                        .asText())
                .isEqualTo(bob.businessId());
    }

    @Test
    void notificationsAreScopedToTheBusiness() {
        Session alice = register();
        Session bob = register();
        String customer = createCustomer(alice, "Due Today Client");
        String invoice = createInvoice(alice, customer, today().minusDays(10).toString(), today().toString())
                .get("id")
                .asText();
        post("/api/v1/invoices/" + invoice + "/send", alice.token(), null);
        post("/api/v1/dev/overdue-check", alice.token(), Map.of());

        assertThat(get("/api/v1/notifications", alice.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isPositive();
        assertThat(get("/api/v1/notifications", bob.token())
                        .getBody()
                        .get("totalItems")
                        .asLong())
                .isZero();
        String aliceNotification = get("/api/v1/notifications", alice.token())
                .getBody()
                .at("/items/0/id")
                .asText();
        assertThat(post("/api/v1/notifications/" + aliceNotification + "/read", bob.token(), null)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }
}
