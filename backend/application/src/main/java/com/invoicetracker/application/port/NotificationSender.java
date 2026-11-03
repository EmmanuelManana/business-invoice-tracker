package com.invoicetracker.application.port;

import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;

/**
 * Delivers a notification over one channel. The default implementations are simulated (they log
 * the message). A real provider (SendGrid, Amazon SES, Twilio, ...) is plugged in by registering a
 * different bean for the channel.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /**
     * @throws NotificationDeliveryException if delivery failed and should be retried
     */
    void send(Notification notification);

    class NotificationDeliveryException extends RuntimeException {
        public NotificationDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
