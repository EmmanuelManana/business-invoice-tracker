package com.invoicetracker.application.port;

import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.invoice.InvoiceSearchCriteria;
import com.invoicetracker.domain.invoice.Invoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId);

    /** Unscoped lookup, for system processes (the overdue job) only. */
    Optional<Invoice> findById(UUID id);

    PageResult<Invoice> search(UUID businessId, InvoiceSearchCriteria criteria);

    boolean existsForCustomer(UUID businessId, UUID customerId);

    /** Persists and flushes, so the returned entity carries its new version. */
    Invoice save(Invoice invoice);

    /** SENT or PARTIALLY_PAID invoices whose due date is before {@code today} (all tenants). */
    List<UUID> findIdsToMarkOverdue(LocalDate today);

    /** Open (SENT, PARTIALLY_PAID, OVERDUE) invoices due on or before {@code date} (all tenants). */
    List<UUID> findOpenIdsDueOnOrBefore(LocalDate date);
}
