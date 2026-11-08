package com.invoicetracker.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: the real application on a random port, talking to a real PostgreSQL
 * in a container (shared by all test classes), exercised over HTTP. Each test registers its own
 * business, so tests are isolated by tenant without truncating tables.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.auth.jwt-secret=integration-test-secret-0123456789-abcdefghij",
            "app.auth.cookie-secure=false",
            // No grace by default, so replaying a rotated token is always treated as theft;
            // RefreshGraceIT covers the grace period.
            "app.auth.refresh-reuse-grace=PT0S",
            "app.overdue.scheduler-enabled=false",
            "app.dev-tools.enabled=true",
            "app.seed.enabled=false",
            "app.rate-limit.auth-requests=100000"
        })
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected ObjectMapper json;

    /**
     * Today in the application time zone, for a business that hasn't moved its demo clock. Each
     * test registers its own business, so one test's clock changes never leak into another.
     */
    protected static java.time.LocalDate today() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"));
    }

    // ---- request helpers -------------------------------------------------------------------

    protected ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        return call(method, path, token, body, new HttpHeaders());
    }

    protected ResponseEntity<JsonNode> call(
            HttpMethod method, String path, String token, Object body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected ResponseEntity<JsonNode> get(String path, String token) {
        return call(HttpMethod.GET, path, token, null);
    }

    protected ResponseEntity<JsonNode> post(String path, String token, Object body) {
        return call(HttpMethod.POST, path, token, body);
    }

    // ---- domain helpers --------------------------------------------------------------------

    protected record Session(String token, String refreshCookie, String businessId, String email) {}

    protected Session register() {
        String email = "owner-" + UUID.randomUUID() + "@example.test";
        var response = post(
                "/api/v1/auth/register",
                null,
                Map.of(
                        "businessName",
                        "Biz " + email.substring(6, 14),
                        "fullName",
                        "Test Owner",
                        "email",
                        email,
                        "password",
                        "Passw0rd!"));
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("register failed: " + response);
        }
        String cookie = refreshCookie(response);
        JsonNode body = response.getBody();
        return new Session(
                body.get("accessToken").asText(),
                cookie,
                body.at("/user/businessId").asText(),
                email);
    }

    protected static String refreshCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        return cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst()
                .orElse(null);
    }

    protected String createCustomer(Session session, String name) {
        var response = post(
                "/api/v1/customers",
                session.token(),
                Map.of(
                        "name",
                        name,
                        "email",
                        name.toLowerCase().replace(' ', '.') + "@client.test",
                        "phone",
                        "+27820000000"));
        return response.getBody().get("id").asText();
    }

    protected Map<String, Object> invoiceBody(String customerId, String issueDate, String dueDate) {
        return Map.of(
                "customerId",
                customerId,
                "issueDate",
                issueDate,
                "dueDate",
                dueDate,
                "notes",
                "Thanks",
                "lineItems",
                List.of(
                        Map.of("description", "Consulting", "quantity", 2, "unitPrice", "1500.00", "vatRate", "0.15"),
                        Map.of("description", "Expenses", "quantity", 1, "unitPrice", "250.00", "vatRate", "0")));
    }

    protected JsonNode createInvoice(Session session, String customerId, String issueDate, String dueDate) {
        var response = post("/api/v1/invoices", session.token(), invoiceBody(customerId, issueDate, dueDate));
        if (response.getStatusCode().value() != 201) {
            throw new IllegalStateException("create invoice failed: " + response);
        }
        return response.getBody();
    }

    protected ResponseEntity<JsonNode> pay(Session session, String invoiceId, String amount, String key) {
        return pay(session, invoiceId, amount, key, today().toString());
    }

    protected ResponseEntity<JsonNode> pay(
            Session session, String invoiceId, String amount, String key, String paidOn) {
        var headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        return call(
                HttpMethod.POST,
                "/api/v1/invoices/" + invoiceId + "/payments",
                session.token(),
                Map.of("amount", amount, "paidOn", paidOn, "method", "EFT"),
                headers);
    }
}
