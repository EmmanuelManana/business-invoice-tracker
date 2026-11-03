package com.invoicetracker.infrastructure.pdf;

import com.invoicetracker.application.port.InvoiceDocumentRenderer;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.common.Address;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.InvoiceLineItem;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Renders a simple A4 tax invoice with OpenPDF. */
@Component
class OpenPdfInvoiceRenderer implements InvoiceDocumentRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color HEADER_BG = new Color(241, 245, 249);

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
    private final Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private final Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private final Font muted = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);

    @Override
    public byte[] render(Invoice invoice, Business business) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("Invoice " + invoice.getNumber());
            document.addCreator(business.getName());
            document.open();

            document.add(header(invoice, business));
            document.add(Chunk.NEWLINE);
            document.add(parties(invoice.getCustomer(), business));
            document.add(Chunk.NEWLINE);
            document.add(lines(invoice));
            document.add(totals(invoice));
            if (invoice.getNotes() != null) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Notes", bold));
                document.add(new Paragraph(invoice.getNotes(), normal));
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render invoice " + invoice.getNumber(), e);
        }
        return out.toByteArray();
    }

    private PdfPTable header(Invoice invoice, Business business) {
        PdfPTable table = new PdfPTable(new float[] {3, 2});
        table.setWidthPercentage(100);
        PdfPCell left = borderless();
        left.addElement(new Paragraph(business.getName(), title));
        if (business.getVatNumber() != null) {
            left.addElement(new Paragraph("VAT No: " + business.getVatNumber(), muted));
        }
        if (business.getRegistrationNumber() != null) {
            left.addElement(new Paragraph("Reg No: " + business.getRegistrationNumber(), muted));
        }
        table.addCell(left);

        PdfPCell right = borderless();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.addElement(rightAligned(new Paragraph("TAX INVOICE", bold)));
        right.addElement(rightAligned(new Paragraph(invoice.getNumber(), normal)));
        right.addElement(rightAligned(new Paragraph("Issued: " + DATE.format(invoice.getIssueDate()), muted)));
        right.addElement(rightAligned(new Paragraph("Due: " + DATE.format(invoice.getDueDate()), muted)));
        right.addElement(rightAligned(
                new Paragraph("Status: " + invoice.getStatus().name().replace('_', ' '), muted)));
        table.addCell(right);
        return table;
    }

    private PdfPTable parties(Customer customer, Business business) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(party("Bill to", customer.getName(), customer.getBillingAddress(), customer.getEmail()));
        table.addCell(party("From", business.getName(), business.getAddress(), business.getEmail()));
        return table;
    }

    private PdfPCell party(String heading, String name, Address address, String email) {
        PdfPCell cell = borderless();
        cell.addElement(new Paragraph(heading.toUpperCase(Locale.ROOT), muted));
        cell.addElement(new Paragraph(name, bold));
        String addressText = Stream.of(
                        address.getLine1(),
                        address.getLine2(),
                        address.getCity(),
                        address.getProvince(),
                        address.getPostalCode())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
        if (!addressText.isEmpty()) {
            cell.addElement(new Paragraph(addressText, normal));
        }
        if (email != null) {
            cell.addElement(new Paragraph(email, normal));
        }
        return cell;
    }

    private PdfPTable lines(Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[] {5, 1.2f, 1.8f, 1.2f, 2});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : new String[] {"Description", "Qty", "Unit price", "VAT", "Amount"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, bold));
            cell.setBackgroundColor(HEADER_BG);
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setPadding(6);
            if (!h.equals("Description")) {
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            table.addCell(cell);
        }
        for (InvoiceLineItem line : invoice.getLineItems()) {
            table.addCell(cell(line.getDescription(), Element.ALIGN_LEFT));
            table.addCell(cell(line.getQuantity().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT));
            table.addCell(cell(money(line.getUnitPrice()), Element.ALIGN_RIGHT));
            table.addCell(cell(percent(line.getVatRate()), Element.ALIGN_RIGHT));
            table.addCell(cell(money(line.getLineSubtotal()), Element.ALIGN_RIGHT));
        }
        return table;
    }

    private PdfPTable totals(Invoice invoice) {
        PdfPTable table = new PdfPTable(new float[] {7, 3});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        addTotal(table, "Subtotal", money(invoice.getSubtotal()), normal);
        addTotal(table, "VAT", money(invoice.getVatTotal()), normal);
        addTotal(table, "Total (" + invoice.getCurrency() + ")", money(invoice.getTotal()), bold);
        addTotal(table, "Paid", money(invoice.getAmountPaid()), normal);
        addTotal(table, "Balance due", money(invoice.balanceDue()), bold);
        return table;
    }

    private void addTotal(PdfPTable table, String label, String value, Font font) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setBorder(PdfPCell.NO_BORDER);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        l.setPadding(4);
        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setBorder(PdfPCell.NO_BORDER);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setPadding(4);
        table.addCell(l);
        table.addCell(v);
    }

    private PdfPCell cell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, normal));
        cell.setBorder(PdfPCell.BOTTOM);
        cell.setBorderColor(HEADER_BG);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private static PdfPCell borderless() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        return cell;
    }

    private static Paragraph rightAligned(Paragraph p) {
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static String money(BigDecimal amount) {
        var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(amount);
    }

    private static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP) + "%";
    }
}
