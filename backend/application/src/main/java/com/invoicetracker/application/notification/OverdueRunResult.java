package com.invoicetracker.application.notification;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Summary of one run of the overdue and reminder job. {@code skipped} means the whole run was
 * skipped because another instance held the lock; {@code notificationsSkipped} counts messages
 * that were not sent because they no longer applied.
 */
public record OverdueRunResult(
        Instant ranAt,
        LocalDate businessDate,
        int invoicesMarkedOverdue,
        int remindersQueued,
        int alertsQueued,
        int notificationsSent,
        int notificationsFailed,
        int notificationsSkipped,
        int errors,
        boolean skipped) {

    static OverdueRunResult skipped(Instant ranAt, LocalDate businessDate) {
        return new OverdueRunResult(ranAt, businessDate, 0, 0, 0, 0, 0, 0, 0, true);
    }
}
