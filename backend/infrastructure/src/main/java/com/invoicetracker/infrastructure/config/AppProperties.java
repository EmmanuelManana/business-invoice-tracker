package com.invoicetracker.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * All application configuration under the {@code app.*} prefix. Every value can be overridden
 * with an environment variable (e.g. {@code APP_AUTH_JWT_SECRET}); see the README for the list.
 * Validated at startup so a misconfigured deployment fails fast.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Auth auth,
        @Valid @DefaultValue Invoices invoices,
        @Valid @DefaultValue Reminders reminders,
        @Valid @DefaultValue Overdue overdue,
        @Valid @DefaultValue Time time,
        @Valid @DefaultValue DevTools devTools,
        @Valid @DefaultValue Seed seed,
        @Valid @DefaultValue Cors cors,
        @Valid @DefaultValue RateLimit rateLimit) {

    public record Auth(
            @NotBlank String jwtSecret,
            @DefaultValue("invoice-tracker") @NotBlank String issuer,
            @DefaultValue("PT15M") @NotNull Duration accessTokenTtl,
            @DefaultValue("P7D") @NotNull Duration refreshTokenTtl,
            /* How long a just-rotated refresh token is still accepted (two tabs refreshing at once). */
            @DefaultValue("PT30S") @NotNull Duration refreshReuseGrace,
            /* Set to false only for local HTTP development. */
            @DefaultValue("true") boolean cookieSecure) {}

    public record Invoices(@DefaultValue("0.15") @DecimalMin("0") @DecimalMax("1") BigDecimal defaultVatRate) {}

    public record Reminders(
            /* Days relative to the due date: negative = before, 0 = on, positive = overdue. */
            @DefaultValue({"-3", "0", "1", "7", "14"}) @NotEmpty List<Integer> offsetsDays,
            @DefaultValue("true") boolean smsEnabled,
            @DefaultValue("3") @Min(1) int maxDeliveryAttempts,
            @DefaultValue("200") @Min(1) int dispatchBatchSize) {}

    public record Overdue(
            @DefaultValue("true") boolean schedulerEnabled,
            @DefaultValue("PT1M") @NotNull Duration interval,
            @DefaultValue("PT15S") @NotNull Duration initialDelay) {}

    public record Time(@DefaultValue("Africa/Johannesburg") @NotBlank String zone) {}

    /** Enables the "simulate time / run overdue check now" endpoints. Never enable in production. */
    public record DevTools(@DefaultValue("false") boolean enabled) {}

    public record Seed(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("demo@acme.co.za") String demoEmail,
            String demoPassword) {}

    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {}

    public record RateLimit(
            /* Requests allowed per client IP per window on the auth endpoints. */
            @DefaultValue("10") @Min(1) int authRequests, @DefaultValue("PT1M") @NotNull Duration authWindow) {}
}
