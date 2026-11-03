package com.invoicetracker.infrastructure.persistence;

import com.invoicetracker.application.dashboard.DashboardSummary;
import com.invoicetracker.application.dashboard.DashboardSummary.AgeingBucket;
import com.invoicetracker.application.dashboard.DashboardSummary.OverdueInvoice;
import com.invoicetracker.application.port.DashboardReadModel;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Dashboard figures computed with SQL aggregates, so cost does not grow with invoices loaded into memory. */
@Component
class JdbcDashboardReadModel implements DashboardReadModel {

    private static final String OPEN = "('SENT', 'PARTIALLY_PAID', 'OVERDUE')";

    private static final String TOTALS_SQL =
            """
            SELECT
              COALESCE(SUM(total - amount_paid), 0)                                                         AS outstanding,
              COALESCE(SUM(total - amount_paid) FILTER (WHERE due_date < CAST(:today AS date)), 0)                        AS overdue,
              COALESCE(SUM(total - amount_paid) FILTER (WHERE due_date >= CAST(:today AS date)), 0)                       AS b_current,
              COUNT(*) FILTER (WHERE due_date >= CAST(:today AS date))                                                    AS c_current,
              COALESCE(SUM(total - amount_paid) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 1 AND 30), 0)       AS b_30,
              COUNT(*) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 1 AND 30)                                    AS c_30,
              COALESCE(SUM(total - amount_paid) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 31 AND 60), 0)      AS b_60,
              COUNT(*) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 31 AND 60)                                   AS c_60,
              COALESCE(SUM(total - amount_paid) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 61 AND 90), 0)      AS b_90,
              COUNT(*) FILTER (WHERE CAST(:today AS date) - due_date BETWEEN 61 AND 90)                                   AS c_90,
              COALESCE(SUM(total - amount_paid) FILTER (WHERE CAST(:today AS date) - due_date > 90), 0)                   AS b_90p,
              COUNT(*) FILTER (WHERE CAST(:today AS date) - due_date > 90)                                                AS c_90p
            FROM invoices
            WHERE business_id = :businessId AND status IN %s
            """
                    .formatted(OPEN);

    private static final String STATUS_COUNTS_SQL =
            "SELECT status, COUNT(*) AS n FROM invoices WHERE business_id = :businessId GROUP BY status";

    private static final String PAID_THIS_MONTH_SQL =
            """
            SELECT COALESCE(SUM(p.amount), 0)
            FROM payments p JOIN invoices i ON i.id = p.invoice_id
            WHERE i.business_id = :businessId AND p.paid_on BETWEEN CAST(:monthStart AS date) AND CAST(:today AS date)
            """;

    private static final String TOP_OVERDUE_SQL =
            """
            SELECT i.id, i.number, c.name AS customer_name, i.due_date, (i.total - i.amount_paid) AS balance
            FROM invoices i JOIN customers c ON c.id = i.customer_id
            WHERE i.business_id = :businessId AND i.status IN %s AND i.due_date < CAST(:today AS date)
            ORDER BY i.due_date ASC, balance DESC
            LIMIT 5
            """
                    .formatted(OPEN);

    private final NamedParameterJdbcTemplate jdbc;

    JdbcDashboardReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DashboardSummary summarise(UUID businessId, String currency, LocalDate today) {
        var params = new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("today", today)
                .addValue("monthStart", today.withDayOfMonth(1));

        Map<String, Object> totals = jdbc.queryForMap(TOTALS_SQL, params);
        List<AgeingBucket> ageing = List.of(
                bucket("current", "Current", totals, "b_current", "c_current"),
                bucket("1-30", "1-30 days", totals, "b_30", "c_30"),
                bucket("31-60", "31-60 days", totals, "b_60", "c_60"),
                bucket("61-90", "61-90 days", totals, "b_90", "c_90"),
                bucket("90+", "90+ days", totals, "b_90p", "c_90p"));

        Map<InvoiceStatus, Long> counts = new EnumMap<>(InvoiceStatus.class);
        for (InvoiceStatus status : InvoiceStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query(STATUS_COUNTS_SQL, params, rs -> {
            counts.put(InvoiceStatus.valueOf(rs.getString("status")), rs.getLong("n"));
        });

        BigDecimal paidThisMonth = jdbc.queryForObject(PAID_THIS_MONTH_SQL, params, BigDecimal.class);

        List<OverdueInvoice> topOverdue = jdbc.query(TOP_OVERDUE_SQL, params, (rs, i) -> {
            LocalDate due = rs.getObject("due_date", LocalDate.class);
            return new OverdueInvoice(
                    rs.getObject("id", UUID.class),
                    rs.getString("number"),
                    rs.getString("customer_name"),
                    due,
                    ChronoUnit.DAYS.between(due, today),
                    rs.getBigDecimal("balance"));
        });

        return new DashboardSummary(
                currency,
                today,
                (BigDecimal) totals.get("outstanding"),
                (BigDecimal) totals.get("overdue"),
                paidThisMonth,
                counts,
                ageing,
                topOverdue);
    }

    private static AgeingBucket bucket(
            String key, String label, Map<String, Object> totals, String amountColumn, String countColumn) {
        return new AgeingBucket(
                key, label, (BigDecimal) totals.get(amountColumn), ((Number) totals.get(countColumn)).longValue());
    }
}
