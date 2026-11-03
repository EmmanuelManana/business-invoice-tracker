package com.invoicetracker.application.port;

import java.util.UUID;

/** Allocates gap-free, sequential invoice numbers per business and year (e.g. INV-2026-0001). */
public interface InvoiceNumberGenerator {

    /** Must participate in the caller's transaction so a rolled-back invoice does not burn a number. */
    String next(UUID businessId, int year);
}
