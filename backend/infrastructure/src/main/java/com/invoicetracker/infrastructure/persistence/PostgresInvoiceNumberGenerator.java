package com.invoicetracker.infrastructure.persistence;

import com.invoicetracker.application.port.InvoiceNumberGenerator;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Allocates numbers with a single atomic upsert. The row lock it takes is held until the calling
 * transaction ends, so concurrent invoice creation for the same business is serialised and a
 * rolled-back invoice releases its number (no gaps).
 */
@Component
class PostgresInvoiceNumberGenerator implements InvoiceNumberGenerator {

    private static final String NEXT_VALUE_SQL =
            """
            INSERT INTO invoice_number_sequences (business_id, year, last_value)
            VALUES (?, ?, 1)
            ON CONFLICT (business_id, year)
            DO UPDATE SET last_value = invoice_number_sequences.last_value + 1
            RETURNING last_value
            """;

    private final JdbcTemplate jdbc;

    PostgresInvoiceNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String next(UUID businessId, int year) {
        Integer value = jdbc.queryForObject(NEXT_VALUE_SQL, Integer.class, businessId, year);
        return "INV-%d-%04d".formatted(year, value);
    }
}
