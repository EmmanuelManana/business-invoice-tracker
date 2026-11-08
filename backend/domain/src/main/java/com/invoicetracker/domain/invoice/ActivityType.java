package com.invoicetracker.domain.invoice;

public enum ActivityType {
    CREATED,
    UPDATED,
    SENT,
    PAYMENT_RECORDED,
    PAID,
    BECAME_OVERDUE,
    /** An overdue status was undone because the demo clock was moved back before the due date. */
    OVERDUE_CLEARED,
    REMINDER_SENT,
    CANCELLED
}
