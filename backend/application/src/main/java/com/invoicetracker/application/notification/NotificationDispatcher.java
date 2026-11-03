package com.invoicetracker.application.notification;

import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.application.port.NotificationRepository;
import com.invoicetracker.application.port.NotificationSender;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.NotificationKind;
import com.invoicetracker.domain.notification.NotificationStatus;
import com.invoicetracker.domain.notification.ReminderStage;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Drains the outbox: hands each PENDING notification to the sender for its channel and records the
 * outcome. Each notification is processed in its own transaction so one failure cannot affect the
 * others. Failed deliveries stay PENDING and are retried on the next run, up to the configured
 * number of attempts.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationRepository notifications;
    private final InvoiceRepository invoices;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);
    private final TransactionOperations tx;
    private final ApplicationSettings settings;
    private final Clock clock;

    public NotificationDispatcher(
            NotificationRepository notifications,
            InvoiceRepository invoices,
            List<NotificationSender> senders,
            TransactionOperations tx,
            ApplicationSettings settings,
            Clock clock) {
        this.notifications = notifications;
        this.invoices = invoices;
        senders.forEach(s -> this.senders.put(s.channel(), s));
        this.tx = tx;
        this.settings = settings;
        this.clock = clock;
    }

    public DispatchResult dispatchPending() {
        int sent = 0;
        int failed = 0;
        for (UUID id : notifications.findPendingIds(settings.dispatchBatchSize())) {
            Boolean ok = tx.execute(status -> dispatchOne(id));
            if (Boolean.TRUE.equals(ok)) {
                sent++;
            } else if (Boolean.FALSE.equals(ok)) {
                failed++;
            }
        }
        return new DispatchResult(sent, failed);
    }

    /** Returns true if sent, false if delivery failed, null if there was nothing to do. */
    private Boolean dispatchOne(UUID id) {
        Notification notification = notifications.findById(id).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return null;
        }
        NotificationSender sender = senders.get(notification.getChannel());
        Instant now = clock.instant();
        try {
            if (sender == null) {
                throw new IllegalStateException("No sender configured for channel " + notification.getChannel());
            }
            sender.send(notification);
            notification.markSent(now);
            notifications.save(notification);
            recordOnInvoice(notification, now);
            return true;
        } catch (RuntimeException e) {
            notification.markFailed(e.getMessage(), settings.maxDeliveryAttempts());
            notifications.save(notification);
            log.warn(
                    "Delivery of notification {} via {} failed (attempt {}/{}): {}",
                    notification.getId(),
                    notification.getChannel(),
                    notification.getAttempts(),
                    settings.maxDeliveryAttempts(),
                    e.getMessage());
            return false;
        }
    }

    private void recordOnInvoice(Notification notification, Instant now) {
        if (notification.getKind() != NotificationKind.PAYMENT_REMINDER || notification.getInvoiceId() == null) {
            return;
        }
        invoices.findById(notification.getInvoiceId()).ifPresent(invoice -> {
            invoice.recordReminderSent(
                    stageLabel(notification.getStage()),
                    notification.getChannel().name(),
                    notification.getRecipient(),
                    now);
            invoices.save(invoice);
        });
    }

    private static String stageLabel(String code) {
        return ReminderStage.parse(code).map(ReminderStage::label).orElse(code);
    }

    public record DispatchResult(int sent, int failed) {}
}
