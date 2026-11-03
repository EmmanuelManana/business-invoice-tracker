package com.invoicetracker.application.dashboard;

import com.invoicetracker.domain.invoice.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DashboardSummary(
        String currency,
        LocalDate asOf,
        BigDecimal totalOutstanding,
        BigDecimal totalOverdue,
        BigDecimal paidThisMonth,
        Map<InvoiceStatus, Long> countsByStatus,
        List<AgeingBucket> ageing,
        List<OverdueInvoice> topOverdue) {

    /** Outstanding balance grouped by how many days past due: current, 1-30, 31-60, 61-90, 90+. */
    public record AgeingBucket(String key, String label, BigDecimal amount, long count) {}

    public record OverdueInvoice(
            UUID id, String number, String customerName, LocalDate dueDate, long daysOverdue, BigDecimal balanceDue) {}
}
