package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicetracker.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** Health checks, API docs, security headers and correlation ids. */
class PlatformIT extends IntegrationTest {

    @Test
    void livenessAndReadinessAreUp() {
        var live = get("/health/live", null);
        var ready = get("/health/ready", null);

        assertThat(live.getStatusCode().value()).isEqualTo(200);
        assertThat(live.getBody().get("status").asText()).isEqualTo("UP");
        assertThat(ready.getStatusCode().value()).isEqualTo(200);
        assertThat(ready.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    void openApiDocumentIsPublished() {
        var docs = get("/v3/api-docs", null);

        assertThat(docs.getStatusCode().value()).isEqualTo(200);
        assertThat(docs.getBody().at("/info/title").asText()).isEqualTo("Invoice Tracker API");
        assertThat(docs.getBody().get("paths").has("/api/v1/invoices")).isTrue();
    }

    @Test
    void apiResponsesCarrySecurityHeadersAndCorrelationId() {
        Session session = register();
        var headers = new HttpHeaders();
        headers.setBearerAuth(session.token());
        headers.set("X-Correlation-Id", "test-correlation-123");

        var response = http.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        HttpHeaders h = response.getHeaders();
        assertThat(h.getFirst("X-Correlation-Id")).isEqualTo("test-correlation-123");
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(h.getFirst("Content-Security-Policy")).contains("default-src 'none'");
        assertThat(h.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void unsafeCorrelationIdsAreReplaced() {
        var headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "bad id with spaces!");

        var response = http.exchange("/health/live", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst("X-Correlation-Id"))
                .doesNotContain(" ")
                .hasSize(36);
    }

    @Test
    void unknownRoutesUnderTheApiRequireAuthentication() {
        assertThat(get("/api/v1/does-not-exist", null).getStatusCode().value()).isEqualTo(401);
    }
}
