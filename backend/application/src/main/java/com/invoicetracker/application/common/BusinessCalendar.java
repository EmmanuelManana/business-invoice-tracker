package com.invoicetracker.application.common;

import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.domain.business.Business;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single source of "today" for business rules (due dates, overdue checks, payment dates).
 * Each business has its own calendar: real time plus that business's demo offset, which is 0
 * unless the demo time machine has been used. Security-related time (token expiry) never goes
 * through here.
 */
@Component
public class BusinessCalendar {

    private final BusinessRepository businesses;
    private final Clock clock;

    public BusinessCalendar(BusinessRepository businesses, Clock clock) {
        this.businesses = businesses;
        this.clock = clock;
    }

    public LocalDate today(UUID businessId) {
        return load(businessId).today(clock);
    }

    public Instant now(UUID businessId) {
        return load(businessId).now(clock);
    }

    public LocalDate today(Business business) {
        return business.today(clock);
    }

    public Instant now(Business business) {
        return business.now(clock);
    }

    /** The real date, ignoring any business's offset. Used by the cross-tenant overdue job. */
    public LocalDate realToday() {
        return LocalDate.now(clock);
    }

    private Business load(UUID businessId) {
        return businesses.findById(businessId).orElseThrow(() -> new NotFoundException("Business", businessId));
    }
}
