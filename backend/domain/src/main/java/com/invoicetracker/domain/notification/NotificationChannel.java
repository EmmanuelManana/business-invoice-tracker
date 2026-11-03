package com.invoicetracker.domain.notification;

public enum NotificationChannel {
    /** Reminder to the customer by email. */
    EMAIL,
    /** Reminder to the customer by SMS. */
    SMS,
    /** Alert shown to the business user in the app's notification centre. */
    IN_APP
}
