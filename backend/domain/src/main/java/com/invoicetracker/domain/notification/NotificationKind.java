package com.invoicetracker.domain.notification;

public enum NotificationKind {
    /** Payment reminder sent to a customer. */
    PAYMENT_REMINDER,
    /** Alert to the business that one of its invoices became overdue. */
    OVERDUE_ALERT
}
