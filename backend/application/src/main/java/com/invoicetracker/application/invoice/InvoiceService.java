package com.invoicetracker.application.invoice;

import com.invoicetracker.application.common.BusinessCalendar;
import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.PreconditionFailedException;
import com.invoicetracker.application.common.Text;
import com.invoicetracker.application.config.ApplicationSettings;
import com.invoicetracker.application.invoice.InvoiceCommands.CancelInvoiceCommand;
import com.invoicetracker.application.invoice.InvoiceCommands.LineItemCommand;
import com.invoicetracker.application.invoice.InvoiceCommands.RecordPaymentCommand;
import com.invoicetracker.application.invoice.InvoiceCommands.SaveInvoiceCommand;
import com.invoicetracker.application.invoice.InvoiceViews.ActivityView;
import com.invoicetracker.application.invoice.InvoiceViews.InvoiceDetail;
import com.invoicetracker.application.invoice.InvoiceViews.InvoiceDocument;
import com.invoicetracker.application.invoice.InvoiceViews.InvoiceSummary;
import com.invoicetracker.application.invoice.InvoiceViews.PaymentOutcome;
import com.invoicetracker.application.invoice.InvoiceViews.PaymentView;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.CustomerRepository;
import com.invoicetracker.application.port.InvoiceDocumentRenderer;
import com.invoicetracker.application.port.InvoiceNumberGenerator;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.common.BusinessRuleViolationException;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.LineItemDraft;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final BusinessRepository businesses;
    private final InvoiceNumberGenerator numberGenerator;
    private final InvoiceDocumentRenderer documentRenderer;
    private final ApplicationSettings settings;
    private final BusinessCalendar calendar;

    public InvoiceService(
            InvoiceRepository invoices,
            CustomerRepository customers,
            BusinessRepository businesses,
            InvoiceNumberGenerator numberGenerator,
            InvoiceDocumentRenderer documentRenderer,
            ApplicationSettings settings,
            BusinessCalendar calendar) {
        this.invoices = invoices;
        this.customers = customers;
        this.businesses = businesses;
        this.numberGenerator = numberGenerator;
        this.documentRenderer = documentRenderer;
        this.settings = settings;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public PageResult<InvoiceSummary> search(CurrentUser user, InvoiceSearchCriteria criteria) {
        LocalDate today = calendar.today(user.businessId());
        return invoices.search(user.businessId(), criteria).map(i -> InvoiceSummary.from(i, today));
    }

    @Transactional(readOnly = true)
    public InvoiceDetail get(CurrentUser user, UUID id) {
        return InvoiceDetail.from(load(user, id), calendar.today(user.businessId()));
    }

    @Transactional
    public InvoiceDetail create(CurrentUser user, SaveInvoiceCommand command) {
        Business business = business(user);
        Customer customer = customer(user, command.customerId());
        LocalDate issueDate = command.issueDate() != null ? command.issueDate() : calendar.today(user.businessId());
        LocalDate dueDate = resolveDueDate(command, issueDate, business);
        String number = numberGenerator.next(business.getId(), issueDate.getYear());

        Invoice invoice = Invoice.createDraft(
                business.getId(),
                customer,
                number,
                issueDate,
                dueDate,
                business.getCurrency(),
                Text.clean(command.notes()),
                toDrafts(command.lineItems()),
                calendar.now(user.businessId()));
        Invoice saved = invoices.save(invoice);
        log.info("Created invoice {} ({}) for business {}", saved.getNumber(), saved.getId(), business.getId());
        return InvoiceDetail.from(saved, calendar.today(user.businessId()));
    }

    /**
     * Updates a draft. {@code expectedVersion} comes from the client's If-Match header; a mismatch
     * means someone else changed the invoice since the client loaded it.
     */
    @Transactional
    public InvoiceDetail update(CurrentUser user, UUID id, long expectedVersion, SaveInvoiceCommand command) {
        Invoice invoice = load(user, id);
        if (invoice.getVersion() != expectedVersion) {
            throw new PreconditionFailedException(expectedVersion, invoice.getVersion());
        }
        Business business = business(user);
        Customer customer = customer(user, command.customerId());
        LocalDate issueDate = command.issueDate() != null ? command.issueDate() : invoice.getIssueDate();
        LocalDate dueDate = resolveDueDate(command, issueDate, business);

        invoice.updateDraft(
                customer,
                issueDate,
                dueDate,
                Text.clean(command.notes()),
                toDrafts(command.lineItems()),
                calendar.now(user.businessId()));
        return InvoiceDetail.from(invoices.save(invoice), calendar.today(user.businessId()));
    }

    @Transactional
    public InvoiceDetail send(CurrentUser user, UUID id) {
        Invoice invoice = load(user, id);
        invoice.send(calendar.now(user.businessId()));
        // An invoice sent after its due date is overdue straight away.
        invoice.markOverdueIfDue(calendar.today(user.businessId()), calendar.now(user.businessId()));
        return InvoiceDetail.from(invoices.save(invoice), calendar.today(user.businessId()));
    }

    @Transactional
    public InvoiceDetail cancel(CurrentUser user, UUID id, CancelInvoiceCommand command) {
        Invoice invoice = load(user, id);
        invoice.cancel(command == null ? null : Text.clean(command.reason()), calendar.now(user.businessId()));
        return InvoiceDetail.from(invoices.save(invoice), calendar.today(user.businessId()));
    }

    @Transactional
    public PaymentOutcome recordPayment(
            CurrentUser user, UUID id, String idempotencyKey, RecordPaymentCommand command) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BusinessRuleViolationException(
                    "idempotency_key_required", "An Idempotency-Key header (1-100 characters) is required");
        }
        if (command.paidOn().isAfter(calendar.today(user.businessId()))) {
            throw new BusinessRuleViolationException("payment_in_future", "Payment date cannot be in the future");
        }
        Invoice invoice = load(user, id);
        Invoice.PaymentResult result = invoice.recordPayment(
                command.amount(),
                command.paidOn(),
                command.method(),
                Text.clean(command.reference()),
                idempotencyKey.trim(),
                calendar.now(user.businessId()));
        Invoice saved = result.replayed() ? invoice : invoices.save(invoice);
        if (!result.replayed()) {
            log.info("Recorded payment {} on invoice {}", result.payment().getId(), saved.getNumber());
        }
        return new PaymentOutcome(
                InvoiceDetail.from(saved, calendar.today(user.businessId())),
                PaymentView.from(result.payment()),
                result.replayed());
    }

    @Transactional(readOnly = true)
    public List<ActivityView> activity(CurrentUser user, UUID id) {
        return load(user, id).getActivities().stream()
                .map(ActivityView::from)
                .toList()
                .reversed();
    }

    @Transactional(readOnly = true)
    public InvoiceDocument document(CurrentUser user, UUID id) {
        Invoice invoice = load(user, id);
        byte[] pdf = documentRenderer.render(invoice, business(user));
        return new InvoiceDocument(invoice.getNumber() + ".pdf", pdf);
    }

    private LocalDate resolveDueDate(SaveInvoiceCommand command, LocalDate issueDate, Business business) {
        return command.dueDate() != null ? command.dueDate() : issueDate.plusDays(business.getPaymentTermsDays());
    }

    private List<LineItemDraft> toDrafts(List<LineItemCommand> lines) {
        return lines.stream()
                .map(l -> new LineItemDraft(
                        Text.clean(l.description()),
                        l.quantity(),
                        l.unitPrice(),
                        l.vatRate() != null ? l.vatRate() : settings.defaultVatRate()))
                .toList();
    }

    private Invoice load(CurrentUser user, UUID id) {
        return invoices.findByIdAndBusinessId(id, user.businessId())
                .orElseThrow(() -> new NotFoundException("Invoice", id));
    }

    private Customer customer(CurrentUser user, UUID customerId) {
        return customers
                .findByIdAndBusinessId(customerId, user.businessId())
                .orElseThrow(() -> new NotFoundException("Customer", customerId));
    }

    private Business business(CurrentUser user) {
        return businesses
                .findById(user.businessId())
                .orElseThrow(() -> new NotFoundException("Business", user.businessId()));
    }
}
