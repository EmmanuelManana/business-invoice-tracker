package com.invoicetracker.domain.notification;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    /** Not sent because it no longer applied, e.g. the invoice was paid before delivery. */
    SKIPPED
}
