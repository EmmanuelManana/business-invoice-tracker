package com.invoicetracker.domain.invoice;

import java.util.EnumSet;
import java.util.Set;

/**
 * Invoice lifecycle.
 *
 * <pre>
 * DRAFT ──send──▶ SENT ──partial──▶ PARTIALLY_PAID ──full──▶ PAID
 *                  │  └──────────full payment─────────────────▲
 *                  └──overdue──▶ OVERDUE ──payments──▶ (stays OVERDUE until) PAID
 * any unpaid state ──cancel──▶ CANCELLED
 * </pre>
 */
public enum InvoiceStatus {
    DRAFT,
    SENT,
    PARTIALLY_PAID,
    OVERDUE,
    PAID,
    CANCELLED;

    /** Statuses that represent money still owed to the business. */
    public static final Set<InvoiceStatus> OPEN = EnumSet.of(SENT, PARTIALLY_PAID, OVERDUE);

    public boolean canTransitionTo(InvoiceStatus target) {
        return switch (this) {
            case DRAFT -> target == SENT || target == CANCELLED;
            case SENT -> target == PARTIALLY_PAID || target == PAID || target == OVERDUE || target == CANCELLED;
            case PARTIALLY_PAID -> target == PAID || target == OVERDUE || target == CANCELLED;
            case OVERDUE -> target == PAID || target == CANCELLED;
            case PAID, CANCELLED -> false;
        };
    }

    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean acceptsPayments() {
        return isOpen();
    }
}
