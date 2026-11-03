package com.invoicetracker.api.web;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.application.invoice.InvoiceCommands.CancelInvoiceCommand;
import com.invoicetracker.application.invoice.InvoiceCommands.RecordPaymentCommand;
import com.invoicetracker.application.invoice.InvoiceCommands.SaveInvoiceCommand;
import com.invoicetracker.application.invoice.InvoiceSearchCriteria;
import com.invoicetracker.application.invoice.InvoiceSearchCriteria.SortField;
import com.invoicetracker.application.invoice.InvoiceService;
import com.invoicetracker.application.invoice.InvoiceViews.ActivityView;
import com.invoicetracker.application.invoice.InvoiceViews.InvoiceDetail;
import com.invoicetracker.application.invoice.InvoiceViews.InvoiceDocument;
import com.invoicetracker.application.invoice.InvoiceViews.InvoiceSummary;
import com.invoicetracker.application.invoice.InvoiceViews.PaymentOutcome;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search, filter, sort and paginate invoices")
    public PageResult<InvoiceSummary> list(
            CurrentUser user,
            @RequestParam(required = false) Set<InvoiceStatus> status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Matches invoice number or customer name") @RequestParam(required = false)
                    String search,
            @Parameter(description = "issueDate, dueDate, number, total, status or customer")
                    @RequestParam(required = false)
                    String sort,
            @Parameter(description = "asc or desc (default desc)") @RequestParam(required = false) String direction,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        SortField sortField = SortField.parse(sort);
        if (sort != null && !sort.isBlank() && sortField == null) {
            throw new IllegalArgumentException("Unsupported sort field: " + sort);
        }
        var criteria = new InvoiceSearchCriteria(
                status,
                customerId,
                from,
                to,
                search,
                sortField,
                "asc".equalsIgnoreCase(direction),
                Paging.of(page, size));
        return service.search(user, criteria);
    }

    @PostMapping
    @Operation(summary = "Create a draft invoice (totals are computed by the server)")
    public ResponseEntity<InvoiceDetail> create(CurrentUser user, @Valid @RequestBody SaveInvoiceCommand command) {
        InvoiceDetail created = service.create(user, command);
        return ResponseEntity.created(URI.create("/api/v1/invoices/" + created.id()))
                .eTag(Etags.of(created.version()))
                .body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceDetail> get(CurrentUser user, @PathVariable UUID id) {
        InvoiceDetail invoice = service.get(user, id);
        return ResponseEntity.ok().eTag(Etags.of(invoice.version())).body(invoice);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft invoice. Requires If-Match with the ETag from the last read.")
    public ResponseEntity<InvoiceDetail> update(
            CurrentUser user,
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody SaveInvoiceCommand command) {
        InvoiceDetail updated = service.update(user, id, Etags.parse(ifMatch), command);
        return ResponseEntity.ok().eTag(Etags.of(updated.version())).body(updated);
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Mark a draft as sent to the customer")
    public ResponseEntity<InvoiceDetail> send(CurrentUser user, @PathVariable UUID id) {
        InvoiceDetail invoice = service.send(user, id);
        return ResponseEntity.ok().eTag(Etags.of(invoice.version())).body(invoice);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<InvoiceDetail> cancel(
            CurrentUser user,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelInvoiceCommand command) {
        InvoiceDetail invoice = service.cancel(user, id, command);
        return ResponseEntity.ok().eTag(Etags.of(invoice.version())).body(invoice);
    }

    @PostMapping("/{id}/payments")
    @Operation(
            summary = "Record a full or partial payment",
            description = "Send a unique Idempotency-Key per payment. Retrying with the same key returns the "
                    + "original result (200) instead of recording the payment twice.")
    public ResponseEntity<PaymentOutcome> recordPayment(
            CurrentUser user,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RecordPaymentCommand command) {
        PaymentOutcome outcome = service.recordPayment(user, id, idempotencyKey, command);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .eTag(Etags.of(outcome.invoice().version()))
                .header("Idempotent-Replayed", Boolean.toString(outcome.replayed()))
                .body(outcome);
    }

    @GetMapping("/{id}/activity")
    @Operation(summary = "Audit history, newest first")
    public List<ActivityView> activity(CurrentUser user, @PathVariable UUID id) {
        return service.activity(user, id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the invoice as a PDF")
    public ResponseEntity<byte[]> pdf(CurrentUser user, @PathVariable UUID id) {
        InvoiceDocument document = service.document(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.fileName())
                                .build()
                                .toString())
                .body(document.content());
    }
}
