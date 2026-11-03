package com.invoicetracker.domain.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paidOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    private String reference;

    /** Client-supplied key that makes "record payment" safe to retry. Unique per invoice. */
    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Payment() {}

    Payment(
            Invoice invoice,
            BigDecimal amount,
            LocalDate paidOn,
            PaymentMethod method,
            String reference,
            String idempotencyKey,
            Instant now) {
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.amount = amount;
        this.paidOn = paidOn;
        this.method = method;
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
