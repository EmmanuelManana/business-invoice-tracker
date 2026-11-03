package com.invoicetracker.application.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Settings the use cases need. Bound from configuration (appsettings / environment variables) by
 * the infrastructure layer and passed in as plain values, so the application layer does not depend
 * on Spring Boot's configuration machinery.
 */
public record ApplicationSettings(
        Duration refreshTokenTtl,
        BigDecimal defaultVatRate,
        List<Integer> reminderOffsetsDays,
        boolean smsRemindersEnabled,
        int maxDeliveryAttempts,
        int dispatchBatchSize) {

    public ApplicationSettings {
        Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl");
        Objects.requireNonNull(defaultVatRate, "defaultVatRate");
        reminderOffsetsDays = List.copyOf(reminderOffsetsDays);
        if (maxDeliveryAttempts < 1) {
            throw new IllegalArgumentException("maxDeliveryAttempts must be at least 1");
        }
        if (dispatchBatchSize < 1) {
            throw new IllegalArgumentException("dispatchBatchSize must be at least 1");
        }
    }

    public static ApplicationSettings defaults() {
        return new ApplicationSettings(
                Duration.ofDays(7), new BigDecimal("0.15"), List.of(-3, 0, 1, 7, 14), true, 3, 200);
    }
}
