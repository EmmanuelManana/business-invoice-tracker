package com.invoicetracker.application.notification;

import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.application.port.JobLock;
import com.invoicetracker.application.port.NotificationRepository;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.ReminderSchedule;
import com.invoicetracker.domain.notification.ReminderStage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The overdue and reminder job. It runs in three idempotent steps:
 *
 * <ol>
 *   <li><b>Detect overdue</b>: SENT or PARTIALLY_PAID invoices past their due date with a balance
 *       become OVERDUE, and the business gets an in-app alert.
 *   <li><b>Queue reminders</b>: for each open invoice, the latest reached stage of the escalation
 *       schedule is looked up and a reminder is queued per channel unless one already exists for
 *       that (invoice, stage, channel).
 *   <li><b>Dispatch</b>: pending outbox rows are handed to the channel senders.
 * </ol>
 *
 * Every invoice is handled in its own transaction, so a bad record is logged and skipped without
 * blocking the rest. All time comes from the injected {@link Clock}, which is what lets tests and
 * the dev tools move time forward.
 */
@Component
public class OverdueProcessor {

    private static final Logger log = LoggerFactory.getLogger(OverdueProcessor.class);
    static final String JOB_NAME = "overdue-processor";

    private final InvoiceRepository invoices;
    private final BusinessRepository businesses;
    private final NotificationRepository notifications;
    private final ReminderComposer composer;
    private final NotificationDispatcher dispatcher;
    private final TransactionOperations tx;
    private final JobLock jobLock;
    private final ApplicationSettings settings;
    private final ReminderSchedule schedule;
    private final Clock clock;

    public OverdueProcessor(
            InvoiceRepository invoices,
            BusinessRepository businesses,
            NotificationRepository notifications,
            ReminderComposer composer,
            NotificationDispatcher dispatcher,
            TransactionOperations tx,
            JobLock jobLock,
            ApplicationSettings settings,
            Clock clock) {
        this.invoices = invoices;
        this.businesses = businesses;
        this.notifications = notifications;
        this.composer = composer;
        this.dispatcher = dispatcher;
        this.tx = tx;
        this.jobLock = jobLock;
        this.settings = settings;
        this.schedule = new ReminderSchedule(settings.reminderOffsetsDays());
        this.clock = clock;
    }

    /** Runs the job unless another instance is already running it (then returns a skipped result). */
    public OverdueRunResult run() {
        return jobLock.tryRun(JOB_NAME, this::runExclusively).orElseGet(() -> {
            log.info("Overdue run skipped: another run is in progress");
            return OverdueRunResult.skipped(clock.instant(), LocalDate.now(clock));
        });
    }

    private OverdueRunResult runExclusively() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        var counters = new Counters();

        for (UUID id : invoices.findIdsToMarkOverdue(today)) {
            safely(id, counters, () -> markOverdue(id, today, now, counters));
        }
        for (UUID id : invoices.findOpenIdsDueOnOrBefore(today.plusDays(schedule.leadDays()))) {
            safely(id, counters, () -> queueReminders(id, today, now, counters));
        }
        NotificationDispatcher.DispatchResult dispatched = dispatcher.dispatchPending();

        var result = new OverdueRunResult(
                now,
                today,
                counters.markedOverdue,
                counters.remindersQueued,
                counters.alertsQueued,
                dispatched.sent(),
                dispatched.failed(),
                dispatched.skipped(),
                counters.errors,
                false);
        if (result.invoicesMarkedOverdue()
                                + result.remindersQueued()
                                + result.notificationsSent()
                                + result.notificationsSkipped()
                        > 0
                || result.errors() > 0) {
            log.info(
                    "Overdue run for {}: {} marked overdue, {} reminders + {} alerts queued, {} sent, {} failed, {} skipped, {} errors",
                    today,
                    result.invoicesMarkedOverdue(),
                    result.remindersQueued(),
                    result.alertsQueued(),
                    result.notificationsSent(),
                    result.notificationsFailed(),
                    result.notificationsSkipped(),
                    result.errors());
        } else {
            log.debug("Overdue run for {}: nothing to do", today);
        }
        return result;
    }

    private void markOverdue(UUID id, LocalDate today, Instant now, Counters counters) {
        tx.executeWithoutResult(status -> invoices.findById(id).ifPresent(invoice -> {
            if (!invoice.markOverdueIfDue(today, now)) {
                return;
            }
            invoices.save(invoice);
            counters.markedOverdue++;
            if (!notifications.exists(invoice.getId(), Notification.OVERDUE_ALERT_STAGE, NotificationChannel.IN_APP)) {
                notifications.save(composer.overdueAlert(invoice, business(invoice), today, now));
                counters.alertsQueued++;
            }
        }));
    }

    private void queueReminders(UUID id, LocalDate today, Instant now, Counters counters) {
        tx.executeWithoutResult(status -> invoices.findById(id).ifPresent(invoice -> {
            if (!invoice.getStatus().isOpen()) {
                return;
            }
            Optional<ReminderStage> stage = schedule.latestDueStage(invoice.getDueDate(), today);
            if (stage.isEmpty()) {
                return;
            }
            Business business = null;
            for (NotificationChannel channel : channelsFor(invoice.getCustomer())) {
                if (notifications.exists(invoice.getId(), stage.get().code(), channel)) {
                    continue;
                }
                if (business == null) {
                    business = business(invoice);
                }
                notifications.save(composer.paymentReminder(invoice, business, stage.get(), channel, today, now));
                counters.remindersQueued++;
            }
        }));
    }

    private List<NotificationChannel> channelsFor(Customer customer) {
        List<NotificationChannel> channels = new ArrayList<>(2);
        channels.add(NotificationChannel.EMAIL);
        if (settings.smsRemindersEnabled() && customer.hasPhone()) {
            channels.add(NotificationChannel.SMS);
        }
        return channels;
    }

    private Business business(Invoice invoice) {
        return businesses
                .findById(invoice.getBusinessId())
                .orElseThrow(() -> new IllegalStateException("Business " + invoice.getBusinessId() + " not found"));
    }

    private void safely(UUID invoiceId, Counters counters, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            counters.errors++;
            log.error("Overdue processing failed for invoice {}", invoiceId, e);
        }
    }

    private static final class Counters {
        int markedOverdue;
        int remindersQueued;
        int alertsQueued;
        int errors;
    }
}
