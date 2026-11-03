package com.invoicetracker.domain.invoice;

public enum ActivityType {
    CREATED,
    UPDATED,
    SENT,
    PAYMENT_RECORDED,
    PAID,
    BECAME_OVERDUE,
    REMINDER_SENT,
    CANCELLED
}
