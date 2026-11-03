package com.invoicetracker.application.port;

import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.invoice.Invoice;

/** Renders an invoice as a printable document (PDF). */
public interface InvoiceDocumentRenderer {

    byte[] render(Invoice invoice, Business business);
}
