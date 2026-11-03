package com.invoicetracker.domain.invoice;

import java.math.BigDecimal;

/** Input for a line item. Monetary results are always computed by the domain, never supplied. */
public record LineItemDraft(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate) {}
