package com.invoicetracker.domain.invoice;

import com.invoicetracker.domain.common.BusinessRuleViolationException;
import com.invoicetracker.domain.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_items")
public class InvoiceLineItem {

    private static final BigDecimal MAX_VAT_RATE = BigDecimal.ONE;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineVat;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Version
    private Long version;

    protected InvoiceLineItem() {}

    static InvoiceLineItem from(Invoice invoice, int position, LineItemDraft draft) {
        if (draft.description() == null || draft.description().isBlank()) {
            throw new BusinessRuleViolationException("line_item_invalid", "Line item description is required");
        }
        if (draft.quantity() == null || draft.quantity().signum() <= 0) {
            throw new BusinessRuleViolationException(
                    "line_item_invalid", "Line item quantity must be greater than zero");
        }
        if (draft.unitPrice() == null || draft.unitPrice().signum() < 0) {
            throw new BusinessRuleViolationException("line_item_invalid", "Line item unit price must not be negative");
        }
        BigDecimal rate = draft.vatRate() == null ? BigDecimal.ZERO : draft.vatRate();
        if (rate.signum() < 0 || rate.compareTo(MAX_VAT_RATE) > 0) {
            throw new BusinessRuleViolationException("line_item_invalid", "VAT rate must be between 0 and 1");
        }

        var item = new InvoiceLineItem();
        item.id = UUID.randomUUID();
        item.invoice = invoice;
        item.position = position;
        item.description = draft.description().trim();
        item.quantity = draft.quantity();
        item.unitPrice = Money.of(draft.unitPrice());
        item.vatRate = rate;
        item.lineSubtotal = Money.lineNet(item.quantity, item.unitPrice);
        item.lineVat = Money.vat(item.lineSubtotal, rate);
        item.lineTotal = item.lineSubtotal.add(item.lineVat);
        return item;
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public BigDecimal getLineVat() {
        return lineVat;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
