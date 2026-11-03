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
import java.time.Instant;
import java.util.UUID;

/** Append-only audit entry describing something that happened to an invoice. */
@Entity
@Table(name = "invoice_activities")
public class InvoiceActivity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Instant occurredAt;

    @Version
    private Long version;

    protected InvoiceActivity() {}

    InvoiceActivity(Invoice invoice, ActivityType type, String message, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.invoice = invoice;
        this.type = type;
        this.message = message;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public ActivityType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
