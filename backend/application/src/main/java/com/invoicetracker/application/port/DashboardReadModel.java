package com.invoicetracker.application.port;

import com.invoicetracker.application.dashboard.DashboardSummary;
import java.time.LocalDate;
import java.util.UUID;

/** Aggregated, read-only view over invoices and payments, computed in the database. */
public interface DashboardReadModel {

    DashboardSummary summarise(UUID businessId, String currency, LocalDate today);
}
