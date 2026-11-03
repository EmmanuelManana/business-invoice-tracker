package com.invoicetracker.application.invoice;

import com.invoicetracker.application.common.Paging;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Filters, sorting and paging for the invoice list. */
public record InvoiceSearchCriteria(
        Set<InvoiceStatus> statuses,
        UUID customerId,
        LocalDate issuedFrom,
        LocalDate issuedTo,
        String search,
        SortField sortBy,
        boolean ascending,
        Paging paging) {

    public InvoiceSearchCriteria {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        sortBy = sortBy == null ? SortField.ISSUE_DATE : sortBy;
        paging = paging == null ? Paging.of(null, null) : paging;
    }

    public enum SortField {
        ISSUE_DATE,
        DUE_DATE,
        NUMBER,
        TOTAL,
        STATUS,
        CUSTOMER;

        /** Accepts API-style names such as {@code dueDate} or {@code due_date}; null if unknown. */
        public static SortField parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalised = value.replaceAll("([a-z])([A-Z])", "$1_$2")
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);
            for (SortField field : values()) {
                if (field.name().equals(normalised)) {
                    return field;
                }
            }
            return null;
        }
    }
}
