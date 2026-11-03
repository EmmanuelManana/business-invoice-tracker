package com.invoicetracker.infrastructure.notification;

import com.invoicetracker.application.port.NotificationSender;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulated delivery channels. Nothing leaves the system: each "send" is a structured log line,
 * and the outbox row (marked SENT by the dispatcher) is the record of what would have been sent.
 *
 * <p>To go live, implement {@link NotificationSender} for a provider (e.g. SendGrid for EMAIL,
 * Twilio for SMS) and register it instead of the simulated bean for that channel.
 */
final class SimulatedSenders {

    private static final Logger log = LoggerFactory.getLogger("notifications.simulated");

    private SimulatedSenders() {}

    /** Masks the local part of an email or the middle of a phone number so logs carry no full PII. */
    static String mask(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        int at = recipient.indexOf('@');
        if (at > 0) {
            return recipient.charAt(0) + "***" + recipient.substring(at);
        }
        return recipient.substring(0, 3) + "****" + recipient.substring(recipient.length() - 2);
    }

    @Component
    static class Email implements NotificationSender {
        @Override
        public NotificationChannel channel() {
            return NotificationChannel.EMAIL;
        }

        @Override
        public void send(Notification n) {
            log.info(
                    "[SIMULATED EMAIL] id={} invoice={} stage={} to={} subject=\"{}\"",
                    n.getId(),
                    n.getInvoiceNumber(),
                    n.getStage(),
                    mask(n.getRecipient()),
                    n.getSubject());
        }
    }

    @Component
    static class Sms implements NotificationSender {
        @Override
        public NotificationChannel channel() {
            return NotificationChannel.SMS;
        }

        @Override
        public void send(Notification n) {
            log.info(
                    "[SIMULATED SMS] id={} invoice={} stage={} to={} chars={}",
                    n.getId(),
                    n.getInvoiceNumber(),
                    n.getStage(),
                    mask(n.getRecipient()),
                    n.getBody().length());
        }
    }

    /** In-app alerts are read from the outbox by the UI, so "delivery" is just recording it. */
    @Component
    static class InApp implements NotificationSender {
        @Override
        public NotificationChannel channel() {
            return NotificationChannel.IN_APP;
        }

        @Override
        public void send(Notification n) {
            log.info("[IN-APP ALERT] id={} invoice={} subject=\"{}\"", n.getId(), n.getInvoiceNumber(), n.getSubject());
        }
    }
}
