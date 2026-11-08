package com.invoicetracker.domain.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicetracker.domain.common.Address;
import com.invoicetracker.domain.common.BusinessRuleViolationException;
import com.invoicetracker.domain.common.InvalidStateTransitionException;
import com.invoicetracker.domain.customer.Customer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    private static final UUID BUSINESS_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-03-01T08:00:00Z");
    private static final LocalDate ISSUE = LocalDate.of(2026, 3, 1);
    private static final LocalDate DUE = LocalDate.of(2026, 3, 31);
    private static final BigDecimal VAT = new BigDecimal("0.15");

    private final Customer customer =
            Customer.create(BUSINESS_ID, "Acme Ltd", "accounts@acme.test", "+27820000000", Address.empty(), NOW);

    private Invoice draft(LineItemDraft... lines) {
        return Invoice.createDraft(
                BUSINESS_ID, customer, "INV-2026-0001", ISSUE, DUE, "ZAR", null, List.of(lines), NOW);
    }

    private static LineItemDraft line(String qty, String price, BigDecimal vat) {
        return new LineItemDraft("Consulting", new BigDecimal(qty), new BigDecimal(price), vat);
    }

    private Invoice sentInvoice(String amount) {
        Invoice invoice = draft(line("1", amount, BigDecimal.ZERO));
        invoice.send(NOW);
        return invoice;
    }

    @Nested
    class Totals {

        @Test
        void computesSubtotalVatAndTotalFromLines() {
            Invoice invoice = draft(line("2", "1500.00", VAT), line("3", "99.99", VAT), line("1", "250.00", null));

            assertThat(invoice.getSubtotal()).isEqualByComparingTo("3549.97");
            // 450.00 + 45.00 (299.97 * 0.15 = 44.9955) + 0
            assertThat(invoice.getVatTotal()).isEqualByComparingTo("495.00");
            assertThat(invoice.getTotal()).isEqualByComparingTo("4044.97");
            assertThat(invoice.balanceDue()).isEqualByComparingTo("4044.97");
        }

        @Test
        void lineTotalsAlwaysSumToInvoiceTotal() {
            Invoice invoice = draft(line("0.333", "10.01", VAT), line("7", "0.07", VAT), line("1.5", "33.33", VAT));

            BigDecimal sum = invoice.getLineItems().stream()
                    .map(InvoiceLineItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(invoice.getTotal());
        }

        @Test
        void rejectsInvalidLines() {
            assertThatThrownBy(() -> draft(line("0", "10", VAT)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("quantity");
            assertThatThrownBy(() -> draft(line("1", "-1", VAT)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("unit price");
            assertThatThrownBy(() -> draft(line("1", "10", new BigDecimal("1.5"))))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("VAT");
            assertThatThrownBy(() -> draft()).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void rejectsDueDateBeforeIssueDate() {
            assertThatThrownBy(() -> Invoice.createDraft(
                            BUSINESS_ID, customer, "INV-1", DUE, ISSUE, "ZAR", null, List.of(line("1", "1", VAT)), NOW))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void rejectsCustomerFromAnotherBusiness() {
            Customer foreign = Customer.create(UUID.randomUUID(), "Other", "o@x.test", null, Address.empty(), NOW);
            assertThatThrownBy(() -> Invoice.createDraft(
                            BUSINESS_ID, foreign, "INV-1", ISSUE, DUE, "ZAR", null, List.of(line("1", "1", VAT)), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void newInvoiceIsDraftWithCreatedActivity() {
            Invoice invoice = draft(line("1", "100", VAT));
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
            assertThat(invoice.getActivities())
                    .extracting(InvoiceActivity::getType)
                    .containsExactly(ActivityType.CREATED);
        }

        @Test
        void draftCanBeEditedAndSent() {
            Invoice invoice = draft(line("1", "100", VAT));
            invoice.updateDraft(customer, ISSUE, DUE, "Thanks", List.of(line("2", "100", VAT)), NOW);
            invoice.send(NOW);

            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(invoice.getTotal()).isEqualByComparingTo("230.00");
            assertThat(invoice.getSentAt()).isEqualTo(NOW);
            assertThat(invoice.getActivities())
                    .extracting(InvoiceActivity::getType)
                    .containsExactly(ActivityType.CREATED, ActivityType.UPDATED, ActivityType.SENT);
        }

        @Test
        void sentInvoiceCannotBeEdited() {
            Invoice invoice = sentInvoice("100");
            assertThatThrownBy(() -> invoice.updateDraft(customer, ISSUE, DUE, null, List.of(line("1", "1", VAT)), NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Only draft");
        }

        @Test
        void cannotSendTwice() {
            Invoice invoice = sentInvoice("100");
            assertThatThrownBy(() -> invoice.send(NOW))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessage("Invoice cannot transition from SENT to SENT");
        }

        @Test
        void cancelledInvoiceHasNoBalanceDue() {
            Invoice partlyPaid = sentInvoice("1000");
            partlyPaid.recordPayment(new BigDecimal("400"), ISSUE, PaymentMethod.EFT, null, "k", NOW);

            partlyPaid.cancel("written off", NOW);

            assertThat(partlyPaid.balanceDue()).isEqualByComparingTo("0");
            assertThat(partlyPaid.getAmountPaid())
                    .as("payments already received are kept")
                    .isEqualByComparingTo("400");
            assertThat(partlyPaid.getTotal()).isEqualByComparingTo("1000");
        }

        @Test
        void unpaidInvoiceCanBeCancelledButPaidCannot() {
            Invoice open = sentInvoice("100");
            open.cancel("Duplicate", NOW);
            assertThat(open.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
            assertThat(open.getCancellationReason()).isEqualTo("Duplicate");

            Invoice paid = sentInvoice("100");
            paid.recordPayment(new BigDecimal("100"), ISSUE, PaymentMethod.EFT, null, "k1", NOW);
            assertThatThrownBy(() -> paid.cancel(null, NOW)).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void draftCanBeCancelled() {
            Invoice invoice = draft(line("1", "100", VAT));
            invoice.cancel(null, NOW);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        }
    }

    @Nested
    class Payments {

        @Test
        void partialThenFullPaymentMovesToPaid() {
            Invoice invoice = sentInvoice("1000");

            invoice.recordPayment(new BigDecimal("400"), ISSUE, PaymentMethod.EFT, "ref-1", "k1", NOW);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
            assertThat(invoice.balanceDue()).isEqualByComparingTo("600.00");

            invoice.recordPayment(new BigDecimal("600"), ISSUE, PaymentMethod.CARD, null, "k2", NOW);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(invoice.balanceDue()).isEqualByComparingTo("0");
            assertThat(invoice.getPaidAt()).isEqualTo(NOW);
            assertThat(invoice.getPayments()).hasSize(2);
        }

        @Test
        void sameIdempotencyKeyDoesNotRecordTwice() {
            Invoice invoice = sentInvoice("1000");

            var first = invoice.recordPayment(new BigDecimal("400"), ISSUE, PaymentMethod.EFT, null, "same", NOW);
            var retry = invoice.recordPayment(new BigDecimal("400"), ISSUE, PaymentMethod.EFT, null, "same", NOW);

            assertThat(first.replayed()).isFalse();
            assertThat(retry.replayed()).isTrue();
            assertThat(retry.payment().getId()).isEqualTo(first.payment().getId());
            assertThat(invoice.getPayments()).hasSize(1);
            assertThat(invoice.getAmountPaid()).isEqualByComparingTo("400");
        }

        @Test
        void reusingKeyWithDifferentAmountIsRejected() {
            Invoice invoice = sentInvoice("1000");
            invoice.recordPayment(new BigDecimal("400"), ISSUE, PaymentMethod.EFT, null, "same", NOW);

            assertThatThrownBy(() ->
                            invoice.recordPayment(new BigDecimal("500"), ISSUE, PaymentMethod.EFT, null, "same", NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("code")
                    .isEqualTo("idempotency_key_reused");
        }

        @Test
        void overpaymentIsRejected() {
            Invoice invoice = sentInvoice("100");
            assertThatThrownBy(() ->
                            invoice.recordPayment(new BigDecimal("100.01"), ISSUE, PaymentMethod.EFT, null, "k", NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("code")
                    .isEqualTo("overpayment");
        }

        @Test
        void draftDoesNotAcceptPayments() {
            Invoice invoice = draft(line("1", "100", VAT));
            assertThatThrownBy(
                            () -> invoice.recordPayment(new BigDecimal("10"), ISSUE, PaymentMethod.EFT, null, "k", NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("code")
                    .isEqualTo("payment_not_allowed");
        }

        @Test
        void partialPaymentOnOverdueInvoiceKeepsItOverdue() {
            Invoice invoice = sentInvoice("1000");
            invoice.markOverdueIfDue(DUE.plusDays(1), NOW);

            invoice.recordPayment(new BigDecimal("100"), DUE.plusDays(1), PaymentMethod.EFT, null, "k", NOW);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

            invoice.recordPayment(new BigDecimal("900"), DUE.plusDays(1), PaymentMethod.EFT, null, "k2", NOW);
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        }
    }

    @Nested
    class OverdueDetection {

        @Test
        void notOverdueOnTheDueDate() {
            Invoice invoice = sentInvoice("100");
            assertThat(invoice.markOverdueIfDue(DUE, NOW)).isFalse();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
        }

        @Test
        void overdueTheDayAfterDueDate() {
            Invoice invoice = sentInvoice("100");
            assertThat(invoice.markOverdueIfDue(DUE.plusDays(1), NOW)).isTrue();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
            assertThat(invoice.getOverdueAt()).isEqualTo(NOW);
            assertThat(invoice.daysOverdue(DUE.plusDays(10))).isEqualTo(10);
            assertThat(invoice.getActivities())
                    .extracting(InvoiceActivity::getType)
                    .contains(ActivityType.BECAME_OVERDUE);
        }

        @Test
        void partiallyPaidInvoiceCanBecomeOverdue() {
            Invoice invoice = sentInvoice("100");
            invoice.recordPayment(new BigDecimal("10"), ISSUE, PaymentMethod.EFT, null, "k", NOW);
            assertThat(invoice.markOverdueIfDue(DUE.plusDays(1), NOW)).isTrue();
        }

        @Test
        void overdueIsClearedWhenTheDueDateHasNotPassed() {
            Invoice unpaid = sentInvoice("100");
            unpaid.markOverdueIfDue(DUE.plusDays(5), NOW); // simulated: 5 days after the due date
            Invoice partlyPaid = sentInvoice("100");
            partlyPaid.recordPayment(new BigDecimal("10"), ISSUE, PaymentMethod.EFT, null, "k", NOW);
            partlyPaid.markOverdueIfDue(DUE.plusDays(5), NOW);

            // Clock reset: back to a date on or before the due date.
            assertThat(unpaid.clearOverdueIfNotYetDue(DUE, NOW)).isTrue();
            assertThat(partlyPaid.clearOverdueIfNotYetDue(DUE.minusDays(3), NOW))
                    .isTrue();

            assertThat(unpaid.getStatus()).isEqualTo(InvoiceStatus.SENT);
            assertThat(unpaid.getOverdueAt()).isNull();
            assertThat(partlyPaid.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
            assertThat(unpaid.getActivities())
                    .extracting(InvoiceActivity::getType)
                    .endsWith(ActivityType.BECAME_OVERDUE, ActivityType.OVERDUE_CLEARED);
        }

        @Test
        void overdueStaysWhenTheDueDateReallyHasPassed() {
            Invoice invoice = sentInvoice("100");
            invoice.markOverdueIfDue(DUE.plusDays(5), NOW);

            assertThat(invoice.clearOverdueIfNotYetDue(DUE.plusDays(1), NOW)).isFalse();
            assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

            Invoice paid = sentInvoice("100");
            paid.recordPayment(new BigDecimal("100"), ISSUE, PaymentMethod.EFT, null, "k", NOW);
            assertThat(paid.clearOverdueIfNotYetDue(ISSUE, NOW))
                    .as("only OVERDUE is touched")
                    .isFalse();
            assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        }

        @Test
        void markingOverdueIsIdempotent() {
            Invoice invoice = sentInvoice("100");
            invoice.markOverdueIfDue(DUE.plusDays(1), NOW);
            assertThat(invoice.markOverdueIfDue(DUE.plusDays(2), NOW)).isFalse();
            assertThat(invoice.getActivities())
                    .filteredOn(a -> a.getType() == ActivityType.BECAME_OVERDUE)
                    .hasSize(1);
        }

        @Test
        void draftsPaidAndCancelledInvoicesNeverBecomeOverdue() {
            Invoice draft = draft(line("1", "100", VAT));
            Invoice paid = sentInvoice("100");
            paid.recordPayment(new BigDecimal("100"), ISSUE, PaymentMethod.EFT, null, "k", NOW);
            Invoice cancelled = sentInvoice("100");
            cancelled.cancel(null, NOW);

            LocalDate muchLater = DUE.plusDays(90);
            assertThat(draft.markOverdueIfDue(muchLater, NOW)).isFalse();
            assertThat(paid.markOverdueIfDue(muchLater, NOW)).isFalse();
            assertThat(cancelled.markOverdueIfDue(muchLater, NOW)).isFalse();
            assertThat(paid.daysOverdue(muchLater)).isZero();
        }
    }
}
