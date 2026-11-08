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

    /**
     * SENT or PARTIALLY_PAID invoices (all tenants) whose due date is before their own business's
     * today, i.e. {@code realToday} plus that business's clock offset.
     */
    List<UUID> findIdsToMarkOverdue(LocalDate realToday);

    /**
     * Open invoices (all tenants) due on or before their business's today plus {@code leadDays},
     * i.e. the ones that may need a reminder.
     */
    List<UUID> findOpenIdsDueSoonOrLate(LocalDate realToday, int leadDays);

    /** OVERDUE invoices of one business whose due date has not passed on {@code today}. */
    List<UUID> findOverdueIdsNotYetDue(UUID businessId, LocalDate today);
}
