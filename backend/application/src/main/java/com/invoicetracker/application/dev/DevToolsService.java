package com.invoicetracker.application.dev;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.notification.OverdueProcessor;
import com.invoicetracker.application.notification.OverdueRunResult;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.domain.business.Business;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo helpers: move a business's calendar forward and run the overdue job on demand. Every
 * operation applies only to the caller's own business, so one business's demo never changes the
 * dates another business sees. The API only exposes these when dev tools are enabled.
 */
@Service
public class DevToolsService {

    private static final Logger log = LoggerFactory.getLogger(DevToolsService.class);
    public static final int MAX_ADVANCE_DAYS = 365;

    private final BusinessRepository businesses;
    private final InvoiceRepository invoices;
    private final OverdueProcessor overdueProcessor;
    private final Clock clock;

    public DevToolsService(
            BusinessRepository businesses, InvoiceRepository invoices, OverdueProcessor overdueProcessor, Clock clock) {
        this.businesses = businesses;
        this.invoices = invoices;
        this.overdueProcessor = overdueProcessor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClockStatus status(CurrentUser user) {
        return ClockStatus.of(load(user), clock, 0);
    }

    @Transactional
    public ClockStatus advanceDays(CurrentUser user, int days) {
        if (days < 1 || days > MAX_ADVANCE_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_ADVANCE_DAYS);
        }
        Business business = load(user);
        business.advanceClock(days);
        businesses.save(business);
        log.info("Dev tools: business {} moved its clock forward {} day(s)", business.getId(), days);
        return ClockStatus.of(business, clock, 0);
    }

    /**
     * Returns the business's calendar to real time. Invoices that only became overdue because of
     * the simulated date (their due date hasn't really passed) go back to SENT / PARTIALLY_PAID.
     */
    @Transactional
    public ClockStatus reset(CurrentUser user) {
        Business business = load(user);
        business.resetClock();
        businesses.save(business);
        LocalDate today = business.today(clock);
        Instant now = business.now(clock);

        int reopened = 0;
        for (UUID id : invoices.findOverdueIdsNotYetDue(business.getId(), today)) {
            var invoice = invoices.findByIdAndBusinessId(id, business.getId()).orElseThrow();
            if (invoice.clearOverdueIfNotYetDue(today, now)) {
                invoices.save(invoice);
                reopened++;
            }
        }
        log.info("Dev tools: business {} reset its clock; {} invoice(s) no longer overdue", business.getId(), reopened);
        return ClockStatus.of(business, clock, reopened);
    }

    /** Runs the overdue job now. It covers every business, each on its own calendar. */
    public OverdueRunResult runOverdueCheck() {
        return overdueProcessor.run();
    }

    private Business load(CurrentUser user) {
        return businesses
                .findById(user.businessId())
                .orElseThrow(() -> new NotFoundException("Business", user.businessId()));
    }

    /**
     * @param invoicesNoLongerOverdue only set by a reset: invoices returned from OVERDUE because
     *     their due date had not really passed
     */
    public record ClockStatus(Instant now, LocalDate today, int offsetDays, int invoicesNoLongerOverdue) {

        static ClockStatus of(Business business, Clock clock, int invoicesNoLongerOverdue) {
            return new ClockStatus(
                    business.now(clock), business.today(clock), business.getClockOffsetDays(), invoicesNoLongerOverdue);
        }
    }
}
