package com.invoicetracker.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.application.port.NotificationSender;
import com.invoicetracker.application.port.NotificationTemplateRenderer.RenderedMessage;
import com.invoicetracker.application.support.InMemoryRepositories;
import com.invoicetracker.application.support.MutableClock;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.common.Address;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.invoice.ActivityType;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.InvoiceActivity;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import com.invoicetracker.domain.invoice.LineItemDraft;
import com.invoicetracker.domain.invoice.PaymentMethod;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.NotificationKind;
import com.invoicetracker.domain.notification.NotificationStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class OverdueProcessorTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    // 2026-03-01 10:00 in Johannesburg
    private static final Instant START = Instant.parse("2026-03-01T08:00:00Z");

    private final MutableClock clock = new MutableClock(START, ZONE);
    private final InMemoryRepositories.Businesses businesses = new InMemoryRepositories.Businesses();
    private final InMemoryRepositories.Invoices invoices = new InMemoryRepositories.Invoices();
    private final InMemoryRepositories.Notifications notifications = new InMemoryRepositories.Notifications();
    private final RecordingSender email = new RecordingSender(NotificationChannel.EMAIL);
    private final RecordingSender sms = new RecordingSender(NotificationChannel.SMS);
    private final RecordingSender inApp = new RecordingSender(NotificationChannel.IN_APP);

    private Business business;
    private Customer customerWithPhone;
    private Customer customerWithoutPhone;

    @BeforeEach
    void setUp() {
        business = businesses.save(Business.register("Acme Studio", "billing@acme.test", START));
        customerWithPhone =
                Customer.create(business.getId(), "Globex", "ap@globex.test", "+27820000001", Address.empty(), START);
        customerWithoutPhone =
                Customer.create(business.getId(), "Initech", "ap@initech.test", null, Address.empty(), START);
    }

    private OverdueProcessor processor(ApplicationSettings settings) {
        var composer = new ReminderComposer((template, model) -> new RenderedMessage(
                template + " " + model.get("invoiceNumber"),
                "Dear %s, %s %s is due"
                        .formatted(model.get("customerName"), model.get("currency"), model.get("balanceDue"))));
        TransactionOperations tx = TransactionOperations.withoutTransaction();
        var dispatcher =
                new NotificationDispatcher(notifications, invoices, List.of(email, sms, inApp), tx, settings, clock);
        return new OverdueProcessor(invoices, businesses, notifications, composer, dispatcher, tx, settings, clock);
    }

    private OverdueProcessor processor() {
        return processor(ApplicationSettings.defaults());
    }

    private Invoice sentInvoiceDueInDays(Customer customer, int days) {
        LocalDate today = LocalDate.now(clock);
        Invoice invoice = Invoice.createDraft(
                business.getId(),
                customer,
                "INV-2026-%04d"
                        .formatted(
                                invoices.findOpenIdsDueOnOrBefore(LocalDate.MAX).size() + 1),
                today,
                today.plusDays(days),
                "ZAR",
                null,
                List.of(new LineItemDraft(
                        "Design work", BigDecimal.ONE, new BigDecimal("1000"), new BigDecimal("0.15"))),
                clock.instant());
        invoice.send(clock.instant());
        return invoices.save(invoice);
    }

    private List<Notification> notificationsFor(Invoice invoice) {
        return notifications.all().stream()
                .filter(n -> n.getInvoiceId().equals(invoice.getId()))
                .toList();
    }

    @Test
    void doesNothingWhileInvoiceIsNotYetInReminderWindow() {
        Invoice invoice = sentInvoiceDueInDays(customerWithPhone, 10);

        OverdueRunResult result = processor().run();

        assertThat(result.invoicesMarkedOverdue()).isZero();
        assertThat(result.remindersQueued()).isZero();
        assertThat(notificationsFor(invoice)).isEmpty();
    }

    @Test
    void sendsUpcomingReminderOnEmailAndSmsThreeDaysBeforeDue() {
        Invoice invoice = sentInvoiceDueInDays(customerWithPhone, 10);
        clock.advanceDays(7);

        OverdueRunResult result = processor().run();

        assertThat(result.remindersQueued()).isEqualTo(2);
        assertThat(result.notificationsSent()).isEqualTo(2);
        assertThat(notificationsFor(invoice))
                .extracting(Notification::getStage, Notification::getChannel, Notification::getStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("D-3", NotificationChannel.EMAIL, NotificationStatus.SENT),
                        org.assertj.core.groups.Tuple.tuple("D-3", NotificationChannel.SMS, NotificationStatus.SENT));
        assertThat(email.sent).singleElement().satisfies(n -> {
            assertThat(n.getRecipient()).isEqualTo("ap@globex.test");
            assertThat(n.getSubject()).startsWith("reminder-upcoming.email");
            assertThat(n.getBody()).contains("Globex").contains("ZAR 1150.00");
        });
        assertThat(sms.sent)
                .singleElement()
                .extracting(Notification::getRecipient)
                .isEqualTo("+27820000001");
        assertThat(invoice.getActivities())
                .filteredOn(a -> a.getType() == ActivityType.REMINDER_SENT)
                .extracting(InvoiceActivity::getMessage)
                .anySatisfy(m -> assertThat(m).contains("3 days before due").contains("EMAIL"));
    }

    @Test
    void runningTwiceOnTheSameDayDoesNotDuplicateReminders() {
        Invoice invoice = sentInvoiceDueInDays(customerWithPhone, 10);
        clock.advanceDays(7);
        OverdueProcessor processor = processor();

        processor.run();
        clock.advance(Duration.ofHours(3));
        OverdueRunResult second = processor.run();

        assertThat(second.remindersQueued()).isZero();
        assertThat(second.notificationsSent()).isZero();
        assertThat(notificationsFor(invoice)).hasSize(2);
        assertThat(email.sent).hasSize(1);
    }

    @Test
    void marksInvoiceOverdueTheDayAfterDueAndAlertsTheBusiness() {
        Invoice invoice = sentInvoiceDueInDays(customerWithoutPhone, 5);
        clock.advanceDays(6);

        OverdueRunResult result = processor().run();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(result.invoicesMarkedOverdue()).isEqualTo(1);
        assertThat(result.alertsQueued()).isEqualTo(1);
        assertThat(notificationsFor(invoice))
                .extracting(Notification::getKind, Notification::getStage, Notification::getChannel)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                NotificationKind.OVERDUE_ALERT, "OVERDUE", NotificationChannel.IN_APP),
                        org.assertj.core.groups.Tuple.tuple(
                                NotificationKind.PAYMENT_REMINDER, "D+1", NotificationChannel.EMAIL));
        assertThat(inApp.sent)
                .singleElement()
                .extracting(Notification::getRecipient)
                .isEqualTo("billing@acme.test");
    }

    @Test
    void escalatesThroughTheScheduleAsTimePasses() {
        Invoice invoice = sentInvoiceDueInDays(customerWithoutPhone, 3);
        OverdueProcessor processor = processor();
        List<String> stagesSeen = new ArrayList<>();

        for (int day = 0; day <= 20; day++) {
            processor.run();
            clock.advanceDays(1);
        }
        notificationsFor(invoice).stream()
                .filter(n -> n.getKind() == NotificationKind.PAYMENT_REMINDER)
                .forEach(n -> stagesSeen.add(n.getStage()));

        assertThat(stagesSeen).containsExactly("D-3", "D0", "D+1", "D+7", "D+14");
        assertThat(email.sent).hasSize(5);
    }

    @Test
    void afterDowntimeOnlyTheLatestStageIsSentNotEveryMissedOne() {
        Invoice invoice = sentInvoiceDueInDays(customerWithoutPhone, 3);
        clock.advanceDays(3 + 30);

        processor().run();

        assertThat(notificationsFor(invoice))
                .filteredOn(n -> n.getKind() == NotificationKind.PAYMENT_REMINDER)
                .extracting(Notification::getStage)
                .containsExactly("D+14");
    }

    @Test
    void paidAndCancelledInvoicesGetNoReminders() {
        Invoice paid = sentInvoiceDueInDays(customerWithPhone, 3);
        paid.recordPayment(new BigDecimal("1150"), LocalDate.now(clock), PaymentMethod.EFT, null, "k", clock.instant());
        Invoice cancelled = sentInvoiceDueInDays(customerWithPhone, 3);
        cancelled.cancel("duplicate", clock.instant());
        clock.advanceDays(10);

        OverdueRunResult result = processor().run();

        assertThat(result.invoicesMarkedOverdue()).isZero();
        assertThat(notifications.all()).isEmpty();
    }

    @Test
    void smsCanBeDisabled() {
        var settings = new ApplicationSettings(Duration.ofDays(7), new BigDecimal("0.15"), List.of(0), false, 3, 100);
        Invoice invoice = sentInvoiceDueInDays(customerWithPhone, 0);

        processor(settings).run();

        assertThat(notificationsFor(invoice))
                .extracting(Notification::getChannel)
                .containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    void failedDeliveriesAreRetriedThenMarkedFailed() {
        email.failNext = Integer.MAX_VALUE;
        Invoice invoice = sentInvoiceDueInDays(customerWithoutPhone, 0);
        OverdueProcessor processor = processor();

        OverdueRunResult first = processor.run();
        processor.run();
        processor.run();
        OverdueRunResult fourth = processor.run();

        assertThat(first.notificationsFailed()).isEqualTo(1);
        assertThat(fourth.notificationsFailed()).isZero();
        assertThat(notificationsFor(invoice)).singleElement().satisfies(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(n.getAttempts()).isEqualTo(3);
            assertThat(n.getLastError()).contains("provider unavailable");
        });
    }

    @Test
    void transientFailureSucceedsOnRetry() {
        email.failNext = 1;
        Invoice invoice = sentInvoiceDueInDays(customerWithoutPhone, 0);
        OverdueProcessor processor = processor();

        processor.run();
        processor.run();

        assertThat(notificationsFor(invoice)).singleElement().satisfies(n -> {
            assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(n.getAttempts()).isEqualTo(2);
        });
    }

    private static final class RecordingSender implements NotificationSender {
        private final NotificationChannel channel;
        private final List<Notification> sent = new ArrayList<>();
        private int failNext;

        RecordingSender(NotificationChannel channel) {
            this.channel = channel;
        }

        @Override
        public NotificationChannel channel() {
            return channel;
        }

        @Override
        public void send(Notification notification) {
            if (failNext > 0) {
                failNext--;
                throw new NotificationDeliveryException("provider unavailable", null);
            }
            sent.add(notification);
        }
    }
}
