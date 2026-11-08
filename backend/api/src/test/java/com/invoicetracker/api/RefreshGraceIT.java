package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicetracker.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

/** Two browser tabs refreshing with the same cookie at once must not sign the user out. */
@TestPropertySource(properties = "app.auth.refresh-reuse-grace=PT1M")
class RefreshGraceIT extends IntegrationTest {

    @Test
    void concurrentRefreshesWithTheSameCookieBothSucceed() {
        Session session = register();

        var tabA = refresh(session.refreshCookie());
        var tabB = refresh(session.refreshCookie()); // the second tab still sends the old cookie

        assertThat(tabA.getStatusCode().value()).isEqualTo(200);
        assertThat(tabB.getStatusCode().value()).isEqualTo(200);
        // Both tabs keep working afterwards; nobody was signed out.
        assertThat(refresh(refreshCookie(tabA)).getStatusCode().value()).isEqualTo(200);
        assertThat(refresh(refreshCookie(tabB)).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void signedOutCookieIsStillRejected() {
        Session session = register();
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.refreshCookie());
        call(HttpMethod.POST, "/api/v1/auth/logout", null, null, headers);

        assertThat(refresh(session.refreshCookie()).getStatusCode().value()).isEqualTo(401);
    }

    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> refresh(String cookie) {
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, headers);
    }
}
