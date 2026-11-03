package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicetracker.api.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** End-to-end overdue detection and reminders, driven by the dev clock and the "run now" endpoint. */
class OverdueJobIT extends IntegrationTest {

    @Test
    void invoiceBecomesOverdueAndRemindersEscalateWithoutDuplicates() {
        Session session = register();
        String customer = createCustomer(session, "Late Payer");
        String id = createInvoice(
                        session,
                        customer,
                        today().toString(),
                        today().plusDays(5).toString())
                .get("id")
                .asText();
        post("/api/v1/invoices/" + id + "/send", session.token(), null);

        // Day 0: nothing is due yet.
        runOverdueCheck(session);
        assertThat(reminderStages(session)).isEmpty();

        // Day 6: one day overdue.
        advanceDays(session, 6);
        JsonNode run = runOverdueCheck(session);
        assertThat(run.get("skipped").asBoolean()).isFalse();
        assertThat(get("/api/v1/invoices/" + id, session.token())
                        .getBody()
                        .get("status")
                        .asText())
                .isEqualTo("OVERDUE");
        assertThat(reminderStages(session)).containsExactlyInAnyOrder("OVERDUE/IN_APP", "D+1/EMAIL", "D+1/SMS");
        assertThat(get("/api/v1/notifications/unread-count", session.token())
                        .getBody()
                        .get("count")
                        .asLong())
                .isEqualTo(3);

        // Running again the same day is a no-op for this invoice.
        runOverdueCheck(session);
        assertThat(reminderStages(session)).hasSize(3);

        // Day 12: escalates to the 7-days-overdue stage.
        advanceDays(session, 6);
        runOverdueCheck(session);
        assertThat(reminderStages(session)).contains("D+7/EMAIL", "D+7/SMS").hasSize(5);

        JsonNode email = null;
        for (JsonNode n :
                get("/api/v1/notifications", session.token()).getBody().get("items")) {
            if (n.get("stage").asText().equals("D+7")
                    && n.get("channel").asText().equals("EMAIL")) {
                email = n;
            }
        }
        assertThat(email).isNotNull();
        assertThat(email.get("status").asText()).isEqualTo("SENT");
        assertThat(email.get("subject").asText()).contains("7 day(s) past due");
        assertThat(email.get("body").asText()).contains("Dear Late Payer").contains("ZAR 3700.00");

        JsonNode activity =
                get("/api/v1/invoices/" + id + "/activity", session.token()).getBody();
        assertThat(activity.findValuesAsText("type")).contains("BECAME_OVERDUE", "REMINDER_SENT");

        // The dashboard reflects the overdue balance in the 1-30 day bucket.
        JsonNode dashboard = get("/api/v1/dashboard/summary", session.token()).getBody();
        assertThat(dashboard.get("totalOverdue").decimalValue()).isEqualByComparingTo("3700.00");
        assertThat(dashboard.at("/ageing/1/count").asLong()).isEqualTo(1);
        assertThat(dashboard.at("/topOverdue/0/id").asText()).isEqualTo(id);

        // Mark all read clears the bell.
        post("/api/v1/notifications/read-all", session.token(), null);
        assertThat(get("/api/v1/notifications/unread-count", session.token())
                        .getBody()
                        .get("count")
                        .asLong())
                .isZero();
    }

    @Test
    void paidInvoicesAreNeverChased() {
        Session session = register();
        String customer = createCustomer(session, "Prompt Payer");
        String id = createInvoice(
                        session,
                        customer,
                        today().toString(),
                        today().plusDays(2).toString())
                .get("id")
                .asText();
        post("/api/v1/invoices/" + id + "/send", session.token(), null);
        pay(session, id, "3700.00", "paid-in-full");

        advanceDays(session, 10);
        runOverdueCheck(session);

        assertThat(get("/api/v1/invoices/" + id, session.token())
                        .getBody()
                        .get("status")
                        .asText())
                .isEqualTo("PAID");
        assertThat(reminderStages(session)).isEmpty();
    }

    @Test
    void devClockCanBeInspectedAndReset() {
        Session session = register();
        JsonNode advanced = advanceDays(session, 3);
        assertThat(advanced.get("offsetDays").asLong()).isEqualTo(3);

        JsonNode reset = post("/api/v1/dev/clock/reset", session.token(), null).getBody();
        assertThat(reset.get("offsetDays").asLong()).isZero();

        assertThat(post("/api/v1/dev/clock/advance", session.token(), Map.of("days", 0))
                        .getStatusCode()
                        .value())
                .isEqualTo(400);
    }

    private JsonNode runOverdueCheck(Session session) {
        var response = post("/api/v1/dev/overdue-check", session.token(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }

    private JsonNode advanceDays(Session session, int days) {
        return post("/api/v1/dev/clock/advance", session.token(), Map.of("days", days))
                .getBody();
    }

    private List<String> reminderStages(Session session) {
        JsonNode items =
                get("/api/v1/notifications?size=100", session.token()).getBody().get("items");
        List<String> stages = new ArrayList<>();
        items.forEach(
                n -> stages.add(n.get("stage").asText() + "/" + n.get("channel").asText()));
        return stages;
    }
}
