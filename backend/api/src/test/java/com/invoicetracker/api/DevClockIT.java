package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicetracker.api.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The demo time machine is per business, and resetting it undoes overdue statuses it caused. */
class DevClockIT extends IntegrationTest {

    @Test
    void movingTheClockOnlyAffectsTheCallersBusiness() {
        Session alice = register();
        Session bob = register();
        String aliceInvoice = sentInvoiceDueTomorrow(alice);
        String bobInvoice = sentInvoiceDueTomorrow(bob);

        post("/api/v1/dev/clock/advance", alice.token(), Map.of("days", 7));
        post("/api/v1/dev/overdue-check", alice.token(), null);

        assertThat(get("/api/v1/business/today", alice.token())
                        .getBody()
                        .get("today")
                        .asText())
                .isEqualTo(today().plusDays(7).toString());
        assertThat(get("/api/v1/business/today", alice.token())
                        .getBody()
                        .get("simulatedDays")
                        .asInt())
                .isEqualTo(7);
        assertThat(get("/api/v1/dev/clock", bob.token())
                        .getBody()
                        .get("offsetDays")
                        .asInt())
                .as("Bob's clock is untouched")
                .isZero();
        assertThat(get("/api/v1/business/today", bob.token())
                        .getBody()
                        .get("today")
                        .asText())
                .isEqualTo(today().toString());

        assertThat(status(alice, aliceInvoice)).isEqualTo("OVERDUE");
        assertThat(status(bob, bobInvoice)).isEqualTo("SENT");
        // Bob's invoice is due tomorrow, so on his real calendar only the "3 days before due"
        // reminder applies. Nothing from Alice's simulated week (overdue alert, D+1, ...) reaches him.
        assertThat(get("/api/v1/notifications", bob.token())
                        .getBody()
                        .get("items")
                        .findValuesAsText("stage"))
                .isNotEmpty()
                .containsOnly("D-3");
    }

    @Test
    void resettingTheClockReturnsNotYetDueInvoicesFromOverdue() {
        Session session = register();
        String notYetDue = sentInvoiceDueTomorrow(session);
        String reallyLate = createInvoice(
                        session,
                        createCustomer(session, "Late Co"),
                        today().minusDays(40).toString(),
                        today().minusDays(10).toString())
                .get("id")
                .asText();
        post("/api/v1/invoices/" + reallyLate + "/send", session.token(), null);

        post("/api/v1/dev/clock/advance", session.token(), Map.of("days", 7));
        post("/api/v1/dev/overdue-check", session.token(), null);
        assertThat(status(session, notYetDue)).isEqualTo("OVERDUE");

        JsonNode reset = post("/api/v1/dev/clock/reset", session.token(), null).getBody();

        assertThat(reset.get("offsetDays").asInt()).isZero();
        assertThat(reset.get("invoicesNoLongerOverdue").asInt()).isEqualTo(1);
        assertThat(status(session, notYetDue)).isEqualTo("SENT");
        assertThat(status(session, reallyLate))
                .as("genuinely late invoices stay overdue")
                .isEqualTo("OVERDUE");
        assertThat(get("/api/v1/invoices/" + notYetDue + "/activity", session.token())
                        .getBody()
                        .findValuesAsText("type"))
                .contains("BECAME_OVERDUE", "OVERDUE_CLEARED");

        JsonNode dashboard = get("/api/v1/dashboard/summary", session.token()).getBody();
        assertThat(dashboard.at("/countsByStatus/OVERDUE").asLong()).isEqualTo(1);
        assertThat(dashboard.at("/countsByStatus/SENT").asLong()).isEqualTo(1);
    }

    @Test
    void paymentDatesFollowTheBusinessCalendar() {
        Session session = register();
        String id = sentInvoiceDueTomorrow(session);
        post("/api/v1/dev/clock/advance", session.token(), Map.of("days", 5));

        // Five days ahead is "today" for this business, so it is not a future date.
        var paid = pay(session, id, "100.00", "future-ok", today().plusDays(5).toString());
        var tooFar = pay(session, id, "100.00", "future-no", today().plusDays(6).toString());

        assertThat(paid.getStatusCode().value()).isEqualTo(201);
        assertThat(tooFar.getStatusCode().value()).isEqualTo(422);
        assertThat(tooFar.getBody().get("code").asText()).isEqualTo("payment_in_future");
    }

    private String sentInvoiceDueTomorrow(Session session) {
        String customer =
                createCustomer(session, "Customer " + session.businessId().substring(0, 8));
        String id = createInvoice(
                        session,
                        customer,
                        today().toString(),
                        today().plusDays(1).toString())
                .get("id")
                .asText();
        post("/api/v1/invoices/" + id + "/send", session.token(), null);
        return id;
    }

    private String status(Session session, String invoiceId) {
        return get("/api/v1/invoices/" + invoiceId, session.token())
                .getBody()
                .get("status")
                .asText();
    }
}
