package com.invoicetracker.domain.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Money arithmetic helpers. All amounts are {@link BigDecimal} with two decimal places, rounded
 * half-up (the convention used on South African tax invoices). Rounding happens per line so that
 * line totals always add up to the invoice total.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {}

    public static BigDecimal of(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount").setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String amount) {
        return of(new BigDecimal(amount));
    }

    /** quantity x unitPrice, rounded to cents. */
    public static BigDecimal lineNet(BigDecimal quantity, BigDecimal unitPrice) {
        return of(quantity.multiply(unitPrice));
    }

    /** VAT on a net amount at the given rate (e.g. 0.15), rounded to cents. */
    public static BigDecimal vat(BigDecimal net, BigDecimal rate) {
        return of(net.multiply(rate));
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount.signum() > 0;
    }
}
