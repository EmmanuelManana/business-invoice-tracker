package com.invoicetracker.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicetracker.api.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class AuthIT extends IntegrationTest {

    @Test
    void registerReturnsAccessTokenAndHttpOnlyRefreshCookie() {
        var response = post(
                "/api/v1/auth/register",
                null,
                Map.of(
                        "businessName",
                        "Acme",
                        "fullName",
                        "Thandi",
                        "email",
                        "thandi-reg@example.test",
                        "password",
                        "Passw0rd!"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(response.getBody().at("/user/businessName").asText()).isEqualTo("Acme");
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("refresh_token=").contains("HttpOnly").contains("SameSite=Strict");
    }

    @Test
    void meRequiresAValidToken() {
        Session session = register();

        assertThat(get("/api/v1/auth/me", session.token())
                        .getBody()
                        .get("email")
                        .asText())
                .isEqualTo(session.email());

        var anonymous = get("/api/v1/auth/me", null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(anonymous.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(anonymous.getBody().get("code").asText()).isEqualTo("unauthorized");

        assertThat(get("/api/v1/auth/me", "not-a-jwt").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void loginFailuresAreGeneric() {
        Session session = register();

        var wrongPassword =
                post("/api/v1/auth/login", null, Map.of("email", session.email(), "password", "WrongPass1"));
        var unknownUser =
                post("/api/v1/auth/login", null, Map.of("email", "nobody@example.test", "password", "WrongPass1"));

        assertThat(wrongPassword.getStatusCode().value()).isEqualTo(401);
        assertThat(unknownUser.getStatusCode().value()).isEqualTo(401);
        assertThat(wrongPassword.getBody().get("detail"))
                .isEqualTo(unknownUser.getBody().get("detail"));

        var ok = post("/api/v1/auth/login", null, Map.of("email", session.email(), "password", "Passw0rd!"));
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void refreshRotatesCookieAndDetectsReuse() {
        Session session = register();
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.refreshCookie());

        var refreshed = call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, headers);
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        String newCookie = refreshCookie(refreshed);
        assertThat(newCookie).isNotEqualTo(session.refreshCookie());

        // Replaying the old (rotated) token is treated as theft: rejected, and all sessions revoked.
        var replay = new HttpHeaders();
        replay.add(HttpHeaders.COOKIE, session.refreshCookie());
        assertThat(call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, replay)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);

        var useNew = new HttpHeaders();
        useNew.add(HttpHeaders.COOKIE, newCookie);
        assertThat(call(HttpMethod.POST, "/api/v1/auth/refresh", null, null, useNew)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
    }

    @Test
    void duplicateEmailIsAConflict() {
        Session session = register();
        var response = post(
                "/api/v1/auth/register",
                null,
                Map.of("businessName", "Other", "fullName", "X", "email", session.email(), "password", "Passw0rd!"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().get("code").asText()).isEqualTo("email_taken");
    }

    @Test
    void validationErrorsListTheInvalidFields() {
        var response = post(
                "/api/v1/auth/register",
                null,
                Map.of("businessName", "", "fullName", "X", "email", "not-an-email", "password", "short"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("code").asText()).isEqualTo("validation_failed");
        assertThat(response.getBody().get("errors").findValuesAsText("field"))
                .contains("businessName", "email", "password");
        assertThat(response.getBody().get("correlationId").asText()).isNotBlank();
    }
}
