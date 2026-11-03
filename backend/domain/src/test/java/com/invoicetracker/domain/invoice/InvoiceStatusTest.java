package com.invoicetracker.domain.invoice;

import static com.invoicetracker.domain.invoice.InvoiceStatus.CANCELLED;
import static com.invoicetracker.domain.invoice.InvoiceStatus.DRAFT;
import static com.invoicetracker.domain.invoice.InvoiceStatus.OVERDUE;
import static com.invoicetracker.domain.invoice.InvoiceStatus.PAID;
import static com.invoicetracker.domain.invoice.InvoiceStatus.PARTIALLY_PAID;
import static com.invoicetracker.domain.invoice.InvoiceStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InvoiceStatusTest {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(SENT, CANCELLED),
            SENT, EnumSet.of(PARTIALLY_PAID, PAID, OVERDUE, CANCELLED),
            PARTIALLY_PAID, EnumSet.of(PAID, OVERDUE, CANCELLED),
            OVERDUE, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.noneOf(InvoiceStatus.class),
            CANCELLED, EnumSet.noneOf(InvoiceStatus.class));

    @ParameterizedTest
    @EnumSource(InvoiceStatus.class)
    void transitionMatrixIsEnforced(InvoiceStatus from) {
        for (InvoiceStatus to : InvoiceStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(ALLOWED.get(from).contains(to));
        }
    }

    @Test
    void openStatusesAreTheOnesThatOweMoney() {
        assertThat(InvoiceStatus.OPEN).containsExactlyInAnyOrder(SENT, PARTIALLY_PAID, OVERDUE);
    }
}
