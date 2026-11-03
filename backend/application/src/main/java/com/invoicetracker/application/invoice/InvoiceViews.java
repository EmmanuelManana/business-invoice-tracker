package com.invoicetracker.application.invoice;

import com.invoicetracker.domain.invoice.ActivityType;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.InvoiceActivity;
import com.invoicetracker.domain.invoice.InvoiceLineItem;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import com.invoicetracker.domain.invoice.Payment;
import com.invoicetracker.domain.invoice.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models returned by the invoice use cases. */
public final class InvoiceViews {

    private InvoiceViews() {}

    public record InvoiceSummary(
            UUID id,
            String number,
            UUID customerId,
            String customerName,
            LocalDate issueDate,
            LocalDate dueDate,
            InvoiceStatus status,
            String currency,
            BigDecimal total,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            long daysOverdue) {

        public static InvoiceSummary from(Invoice i, LocalDate today) {
            return new InvoiceSummary(
                    i.getId(),
                    i.getNumber(),
                    i.getCustomer().getId(),
                    i.getCustomer().getName(),
                    i.getIssueDate(),
                    i.getDueDate(),
                    i.getStatus(),
                    i.getCurrency(),
                    i.getTotal(),
                    i.getAmountPaid(),
                    i.balanceDue(),
                    i.daysOverdue(today));
        }
    }

    public record CustomerRef(UUID id, String name, String email) {}

    public record LineItemView(
            UUID id,
            int position,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal lineSubtotal,
            BigDecimal lineVat,
            BigDecimal lineTotal) {

        static LineItemView from(InvoiceLineItem l) {
            return new LineItemView(
                    l.getId(),
                    l.getPosition(),
                    l.getDescription(),
                    l.getQuantity(),
                    l.getUnitPrice(),
                    l.getVatRate(),
                    l.getLineSubtotal(),
                    l.getLineVat(),
                    l.getLineTotal());
        }
    }

    public record PaymentView(
            UUID id, BigDecimal amount, LocalDate paidOn, PaymentMethod method, String reference, Instant createdAt) {

        public static PaymentView from(Payment p) {
            return new PaymentView(
                    p.getId(), p.getAmount(), p.getPaidOn(), p.getMethod(), p.getReference(), p.getCreatedAt());
        }
    }

    public record ActivityView(UUID id, ActivityType type, String message, Instant occurredAt) {

        static ActivityView from(InvoiceActivity a) {
            return new ActivityView(a.getId(), a.getType(), a.getMessage(), a.getOccurredAt());
        }
    }

    public record InvoiceDetail(
            UUID id,
            String number,
            InvoiceStatus status,
            CustomerRef customer,
            LocalDate issueDate,
            LocalDate dueDate,
            String currency,
            String notes,
            List<LineItemView> lineItems,
            List<PaymentView> payments,
            BigDecimal subtotal,
            BigDecimal vatTotal,
            BigDecimal total,
            BigDecimal amountPaid,
            BigDecimal balanceDue,
            long daysOverdue,
            Instant sentAt,
            Instant paidAt,
            Instant overdueAt,
            Instant cancelledAt,
            String cancellationReason,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        public static InvoiceDetail from(Invoice i, LocalDate today) {
            var c = i.getCustomer();
            return new InvoiceDetail(
                    i.getId(),
                    i.getNumber(),
                    i.getStatus(),
                    new CustomerRef(c.getId(), c.getName(), c.getEmail()),
                    i.getIssueDate(),
                    i.getDueDate(),
                    i.getCurrency(),
                    i.getNotes(),
                    i.getLineItems().stream().map(LineItemView::from).toList(),
                    i.getPayments().stream().map(PaymentView::from).toList(),
                    i.getSubtotal(),
                    i.getVatTotal(),
                    i.getTotal(),
                    i.getAmountPaid(),
                    i.balanceDue(),
                    i.daysOverdue(today),
                    i.getSentAt(),
                    i.getPaidAt(),
                    i.getOverdueAt(),
                    i.getCancelledAt(),
                    i.getCancellationReason(),
                    i.getCreatedAt(),
                    i.getUpdatedAt(),
                    i.getVersion());
        }
    }

    /** Result of recording a payment; {@code replayed} is true for an idempotent retry. */
    public record PaymentOutcome(InvoiceDetail invoice, PaymentView payment, boolean replayed) {}

    public record InvoiceDocument(String fileName, byte[] content) {}
}
