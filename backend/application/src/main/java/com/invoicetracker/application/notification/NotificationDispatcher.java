package com.invoicetracker.application.notification;

import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.application.port.NotificationRepository;
import com.invoicetracker.application.port.NotificationSender;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.InvoiceStatus;
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
import java.util.Optional;
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
 *
 * <p>Right before sending, the invoice is checked again. A reminder can sit in the outbox for a
 * while (a failed attempt is retried on the next run), and the customer may have paid or the
 * invoice may have been cancelled in the meantime. Such notifications are skipped, not sent.
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
        int skipped = 0;
        for (UUID id : notifications.findPendingIds(settings.dispatchBatchSize())) {
            Outcome outcome = tx.execute(status -> dispatchOne(id));
            if (outcome == Outcome.SENT) {
                sent++;
            } else if (outcome == Outcome.FAILED) {
                failed++;
            } else if (outcome == Outcome.SKIPPED) {
                skipped++;
            }
        }
        return new DispatchResult(sent, failed, skipped);
    }

    private Outcome dispatchOne(UUID id) {
        Notification notification = notifications.findById(id).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return Outcome.NOTHING_TO_DO;
        }
        Optional<Invoice> invoice =
                Optional.ofNullable(notification.getInvoiceId()).flatMap(invoices::findById);
        Optional<String> skipReason = invoice.flatMap(i -> reasonToSkip(notification, i));
        if (skipReason.isPresent()) {
            notification.markSkipped(skipReason.get());
            notifications.save(notification);
            log.info(
                    "Skipped notification {} for invoice {}: {}",
                    notification.getId(),
                    notification.getInvoiceNumber(),
                    skipReason.get());
            return Outcome.SKIPPED;
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
            invoice.ifPresent(i -> recordOnInvoice(notification, i, now));
            return Outcome.SENT;
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
            return Outcome.FAILED;
        }
    }

    /** Why this notification should no longer be sent, given the invoice's current state. */
    static Optional<String> reasonToSkip(Notification notification, Invoice invoice) {
        InvoiceStatus status = invoice.getStatus();
        if (notification.getKind() == NotificationKind.PAYMENT_REMINDER && !status.isOpen()) {
            return Optional.of("invoice is " + status + "; nothing is owed");
        }
        if (notification.getKind() == NotificationKind.OVERDUE_ALERT && status != InvoiceStatus.OVERDUE) {
            return Optional.of("invoice is " + status + "; no longer overdue");
        }
        return Optional.empty();
    }

    private void recordOnInvoice(Notification notification, Invoice invoice, Instant now) {
        if (notification.getKind() != NotificationKind.PAYMENT_REMINDER) {
            return;
        }
        invoice.recordReminderSent(
                stageLabel(notification.getStage()),
                notification.getChannel().name(),
                notification.getRecipient(),
                now);
        invoices.save(invoice);
    }

    private static String stageLabel(String code) {
        return ReminderStage.parse(code).map(ReminderStage::label).orElse(code);
    }

    private enum Outcome {
        SENT,
        FAILED,
        SKIPPED,
        NOTHING_TO_DO
    }

    public record DispatchResult(int sent, int failed, int skipped) {}
}
