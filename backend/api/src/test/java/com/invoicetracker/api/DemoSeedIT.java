package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicetracker.api.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The demo seeder runs on start-up (as in docker compose) and produces a usable account. */
@TestPropertySource(
        properties = {
            "app.seed.enabled=true",
            "app.seed.demo-email=seed-it@acme.test",
            "app.seed.demo-password=Seed@12345"
        })
class DemoSeedIT extends IntegrationTest {

    @Test
    void seededDemoBusinessCanSignInAndHasInvoicesInEveryStatus() {
        var login = post("/api/v1/auth/login", null, Map.of("email", "seed-it@acme.test", "password", "Seed@12345"));
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String token = login.getBody().get("accessToken").asText();

        JsonNode summary = get("/api/v1/dashboard/summary", token).getBody();
        for (String status : new String[] {"DRAFT", "SENT", "PARTIALLY_PAID", "OVERDUE", "PAID", "CANCELLED"}) {
            assertThat(summary.at("/countsByStatus/" + status).asLong())
                    .as(status)
                    .isPositive();
        }
        assertThat(summary.get("totalOverdue").decimalValue()).isPositive();
        assertThat(get("/api/v1/customers", token).getBody().get("totalItems").asLong())
                .isEqualTo(8);
    }
}
