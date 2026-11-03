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

    /**
     * Behind nginx the SPA and API share an origin under any hostname. The proxy forwards the
     * public host and port, so these requests must not be treated as cross-origin.
     */
    @Test
    void proxiedSameOriginRequestsPassCorsButForeignOriginsDoNot() {
        String credentials = "{\"email\":\"nobody@example.test\",\"password\":\"Wrong123\"}";

        var sameOrigin = new HttpHeaders();
        sameOrigin.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        sameOrigin.setOrigin("http://invoices.example:3000");
        sameOrigin.set("X-Forwarded-Host", "invoices.example");
        sameOrigin.set("X-Forwarded-Port", "3000");
        sameOrigin.set("X-Forwarded-Proto", "http");
        var proxied = http.exchange(
                "/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(credentials, sameOrigin), String.class);
        assertThat(proxied.getStatusCode().value()).as("reaches the controller").isEqualTo(401);

        var foreign = new HttpHeaders();
        foreign.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        foreign.setOrigin("http://evil.example");
        var rejected = http.exchange(
                "/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(credentials, foreign), String.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void unknownRoutesUnderTheApiRequireAuthentication() {
        assertThat(get("/api/v1/does-not-exist", null).getStatusCode().value()).isEqualTo(401);
    }
}
