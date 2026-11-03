package com.invoicetracker.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private Instant now = Instant.parse("2026-03-01T08:00:00Z");
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    };

    private final AuthRateLimitFilter filter =
            new AuthRateLimitFilter(3, Duration.ofMinutes(1), clock, new ObjectMapper().findAndRegisterModules());

    private int login(String ip) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void blocksAfterLimitWithinWindowAndRecoversAfterIt() throws Exception {
        assertThat(login("10.0.0.1")).isEqualTo(200);
        assertThat(login("10.0.0.1")).isEqualTo(200);
        assertThat(login("10.0.0.1")).isEqualTo(200);
        assertThat(login("10.0.0.1")).isEqualTo(429);

        now = now.plusSeconds(61);
        assertThat(login("10.0.0.1")).isEqualTo(200);
    }

    @Test
    void limitsArePerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("10.0.0.1");
        }
        assertThat(login("10.0.0.1")).isEqualTo(429);
        assertThat(login("10.0.0.2")).isEqualTo(200);
    }

    @Test
    void rejectedResponseIsAProblemWithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("10.0.0.9");
        }
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("10.0.0.9");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"rate_limited\"");
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("GET", "/api/v1/invoices");
            request.setRemoteAddr("10.0.0.3");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
