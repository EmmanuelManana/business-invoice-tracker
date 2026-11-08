package com.invoicetracker.domain.invoice;

import com.invoicetracker.domain.common.BusinessRuleViolationException;
import com.invoicetracker.domain.common.InvalidStateTransitionException;
import com.invoicetracker.domain.common.Money;
import com.invoicetracker.domain.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Invoice aggregate root. All state changes go through methods on this class so that totals,
 * status transitions and the audit trail can never drift out of sync.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    public static final int MAX_LINE_ITEMS = 100;

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal vatTotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid;

    private Instant sentAt;
    private Instant paidAt;
    private Instant overdueAt;
    private Instant cancelledAt;
    private String cancellationReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paidOn ASC, createdAt ASC")
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC")
    private List<InvoiceActivity> activities = new ArrayList<>();

    protected Invoice() {}

    public static Invoice createDraft(
            UUID businessId,
            Customer customer,
            String number,
            LocalDate issueDate,
            LocalDate dueDate,
            String currency,
            String notes,
            List<LineItemDraft> lines,
            Instant now) {
        Objects.requireNonNull(customer, "customer");
        if (!customer.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException("Customer belongs to a different business");
        }
        var invoice = new Invoice();
        invoice.id = UUID.randomUUID();
        invoice.businessId = businessId;
        invoice.number = number;
        invoice.currency = currency;
        invoice.status = InvoiceStatus.DRAFT;
        invoice.amountPaid = Money.ZERO;
        invoice.createdAt = now;
        invoice.applyDraftDetails(customer, issueDate, dueDate, notes, lines, now);
        invoice.log(ActivityType.CREATED, "Invoice %s created".formatted(number), now);
        return invoice;
    }

    public void updateDraft(
            Customer customer,
            LocalDate issueDate,
            LocalDate dueDate,
            String notes,
            List<LineItemDraft> lines,
            Instant now) {
        if (status != InvoiceStatus.DRAFT) {
            throw new BusinessRuleViolationException(
                    "invoice_not_editable", "Only draft invoices can be edited (current status: %s)".formatted(status));
        }
        if (!customer.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException("Customer belongs to a different business");
        }
        applyDraftDetails(customer, issueDate, dueDate, notes, lines, now);
        log(ActivityType.UPDATED, "Draft updated", now);
    }

    private void applyDraftDetails(
            Customer customer,
            LocalDate issueDate,
            LocalDate dueDate,
            String notes,
            List<LineItemDraft> lines,
            Instant now) {
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(dueDate, "dueDate");
        if (dueDate.isBefore(issueDate)) {
            throw new BusinessRuleViolationException("invalid_due_date", "Due date cannot be before the issue date");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleViolationException("line_items_required", "An invoice needs at least one line item");
        }
        if (lines.size() > MAX_LINE_ITEMS) {
            throw new BusinessRuleViolationException(
                    "too_many_line_items", "An invoice can have at most %d line items".formatted(MAX_LINE_ITEMS));
        }
        this.customer = customer;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.lineItems.clear();
        for (int i = 0; i < lines.size(); i++) {
            this.lineItems.add(InvoiceLineItem.from(this, i + 1, lines.get(i)));
        }
        recalculateTotals();
        this.updatedAt = now;
    }

    private void recalculateTotals() {
        this.subtotal = lineItems.stream().map(InvoiceLineItem::getLineSubtotal).reduce(Money.ZERO, BigDecimal::add);
        this.vatTotal = lineItems.stream().map(InvoiceLineItem::getLineVat).reduce(Money.ZERO, BigDecimal::add);
        this.total = subtotal.add(vatTotal);
    }

    public void send(Instant now) {
        transitionTo(InvoiceStatus.SENT);
        this.sentAt = now;
        this.updatedAt = now;
        log(ActivityType.SENT, "Invoice sent to %s".formatted(customer.getEmail()), now);
    }

    /**
     * Records a payment. Calling this again with the same idempotency key returns the original
     * payment and changes nothing, so clients can safely retry after a timeout.
     */
    public PaymentResult recordPayment(
            BigDecimal amount,
            LocalDate paidOn,
            PaymentMethod method,
            String reference,
            String idempotencyKey,
            Instant now) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        Optional<Payment> existing = findPaymentByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Payment previous = existing.get();
            if (previous.getAmount().compareTo(Money.of(amount)) != 0) {
                throw new BusinessRuleViolationException(
                        "idempotency_key_reused",
                        "This idempotency key was already used for a payment with a different amount");
            }
            return new PaymentResult(previous, true);
        }
        if (!status.acceptsPayments()) {
            throw new BusinessRuleViolationException(
                    "payment_not_allowed", "Payments cannot be recorded on a %s invoice".formatted(status));
        }
        BigDecimal value = Money.of(amount);
        if (!Money.isPositive(value)) {
            throw new BusinessRuleViolationException("invalid_payment_amount", "Payment amount must be positive");
        }
        if (value.compareTo(balanceDue()) > 0) {
            throw new BusinessRuleViolationException(
                    "overpayment", "Payment of %s exceeds the balance due of %s".formatted(value, balanceDue()));
        }
        Objects.requireNonNull(paidOn, "paidOn");
        Objects.requireNonNull(method, "method");

        var payment = new Payment(this, value, paidOn, method, reference, idempotencyKey, now);
        payments.add(payment);
        amountPaid = amountPaid.add(value);
        updatedAt = now;
        log(
                ActivityType.PAYMENT_RECORDED,
                "Payment of %s %s received via %s".formatted(currency, value.toPlainString(), method),
                now);

        if (balanceDue().signum() == 0) {
            transitionTo(InvoiceStatus.PAID);
            paidAt = now;
            log(ActivityType.PAID, "Invoice paid in full", now);
        } else if (status == InvoiceStatus.SENT) {
            transitionTo(InvoiceStatus.PARTIALLY_PAID);
        }
        return new PaymentResult(payment, false);
    }

    /**
     * Moves the invoice to OVERDUE if it is unpaid and the due date has passed.
     *
     * @return true if the status changed
     */
    public boolean markOverdueIfDue(LocalDate today, Instant now) {
        boolean eligible = (status == InvoiceStatus.SENT || status == InvoiceStatus.PARTIALLY_PAID)
                && dueDate.isBefore(today)
                && balanceDue().signum() > 0;
        if (!eligible) {
            return false;
        }
        transitionTo(InvoiceStatus.OVERDUE);
        overdueAt = now;
        updatedAt = now;
        log(
                ActivityType.BECAME_OVERDUE,
                "Invoice became overdue (due %s, balance %s %s)".formatted(dueDate, currency, balanceDue()),
                now);
        return true;
    }

    /**
     * Reverses an OVERDUE status when the due date has not actually passed on {@code today}. This
     * only happens when the demo clock is moved back to real time: invoices that went overdue in
     * simulated time return to SENT (or PARTIALLY_PAID if money was received).
     *
     * <p>Deliberately not part of the normal status machine: it is a correction, not a step a user
     * can take.
     *
     * @return true if the status changed
     */
    public boolean clearOverdueIfNotYetDue(LocalDate today, Instant now) {
        if (status != InvoiceStatus.OVERDUE || dueDate.isBefore(today)) {
            return false;
        }
        status = amountPaid.signum() > 0 ? InvoiceStatus.PARTIALLY_PAID : InvoiceStatus.SENT;
        overdueAt = null;
        updatedAt = now;
        log(
                ActivityType.OVERDUE_CLEARED,
                "No longer overdue: due %s has not passed yet (demo clock reset)".formatted(dueDate),
                now);
        return true;
    }

    public void cancel(String reason, Instant now) {
        transitionTo(InvoiceStatus.CANCELLED);
        cancelledAt = now;
        cancellationReason = reason == null || reason.isBlank() ? null : reason.trim();
        updatedAt = now;
        log(
                ActivityType.CANCELLED,
                cancellationReason == null ? "Invoice cancelled" : "Invoice cancelled: " + cancellationReason,
                now);
    }

    public void recordReminderSent(String stageLabel, String channel, String recipient, Instant now) {
        log(ActivityType.REMINDER_SENT, "%s reminder sent via %s to %s".formatted(stageLabel, channel, recipient), now);
    }

    private void transitionTo(InvoiceStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Invoice", status, target);
        }
        status = target;
    }

    private void log(ActivityType type, String message, Instant now) {
        activities.add(new InvoiceActivity(this, type, message, now));
    }

    public Optional<Payment> findPaymentByIdempotencyKey(String key) {
        return payments.stream().filter(p -> p.getIdempotencyKey().equals(key)).findFirst();
    }

    /** What the customer still owes. Nothing is owed on a cancelled invoice. */
    public BigDecimal balanceDue() {
        if (status == InvoiceStatus.CANCELLED) {
            return Money.ZERO;
        }
        return total.subtract(amountPaid);
    }

    /** Days past the due date, or 0 if not yet due or nothing is owed. */
    public long daysOverdue(LocalDate today) {
        if (!status.isOpen() || !dueDate.isBefore(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, today);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getVatTotal() {
        return vatTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getOverdueAt() {
        return overdueAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version == null ? 0 : version;
    }

    public List<InvoiceLineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    public List<Payment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    public List<InvoiceActivity> getActivities() {
        return Collections.unmodifiableList(activities);
    }

    /** Outcome of {@link #recordPayment}; {@code replayed} is true for an idempotent retry. */
    public record PaymentResult(Payment payment, boolean replayed) {}
}
