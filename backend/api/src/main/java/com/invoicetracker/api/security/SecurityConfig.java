package com.invoicetracker.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicetracker.api.web.ProblemTypes;
import com.invoicetracker.infrastructure.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"
    };

    /** Health checks and API docs: public, and without the strict API CSP so Swagger UI can load. */
    @Bean
    @Order(1)
    SecurityFilterChain operationsChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/health/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /**
     * The API itself: stateless JWT bearer authentication. CSRF protection is not needed for
     * bearer tokens; the only cookie (the refresh token) is HttpOnly, SameSite=Strict and scoped to
     * the auth path.
     */
    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS)
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, ex) -> writeProblem(
                                response,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "unauthorized",
                                "Authentication required",
                                "A valid access token is required.")))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> writeProblem(
                                response,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "unauthorized",
                                "Authentication required",
                                "A valid access token is required."))
                        .accessDeniedHandler((request, response, e) -> writeProblem(
                                response,
                                objectMapper,
                                HttpStatus.FORBIDDEN,
                                "forbidden",
                                "Forbidden",
                                "You do not have access to this resource.")))
                .headers(headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "If-Match", "Idempotency-Key", "X-Correlation-Id"));
        config.setExposedHeaders(List.of("ETag", "Location", "X-Correlation-Id", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void writeProblem(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String title,
            String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ProblemTypes.problem(status, code, title, detail));
    }
}
