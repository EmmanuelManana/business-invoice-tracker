package com.invoicetracker.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicetracker.api.web.ProblemTypes;
import com.invoicetracker.infrastructure.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per client IP on the authentication endpoints, to slow down credential
 * stuffing and brute force. In-memory, so the limit is per instance; behind several instances a
 * shared store (Redis) or the API gateway should enforce it instead.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH = "/api/v1/auth/";
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AppProperties properties, ObjectMapper objectMapper) {
        this(
                properties.rateLimit().authRequests(),
                properties.rateLimit().authWindow(),
                Clock.systemUTC(),
                objectMapper);
    }

    AuthRateLimitFilter(int limit, Duration window, Clock clock, ObjectMapper objectMapper) {
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && request.getRequestURI().startsWith(AUTH_PATH));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start >= windowMillis);
        }
        Window window = windows.compute(
                request.getRemoteAddr(),
                (ip, w) -> w == null || now - w.start >= windowMillis ? new Window(now, 1) : w.increment());

        if (window.count > limit) {
            long retryAfterSeconds = Math.max(1, (window.start + windowMillis - now + 999) / 1000);
            ProblemDetail problem = ProblemTypes.problem(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "rate_limited",
                    "Too many requests",
                    "Too many authentication attempts. Try again in %d seconds.".formatted(retryAfterSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), problem);
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long start, int count) {
        Window increment() {
            return new Window(start, count + 1);
        }
    }
}
