package com.invoicetracker.application.dashboard;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.DashboardReadModel;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final DashboardReadModel readModel;
    private final BusinessRepository businesses;
    private final Clock clock;

    public DashboardService(DashboardReadModel readModel, BusinessRepository businesses, Clock clock) {
        this.readModel = readModel;
        this.businesses = businesses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(CurrentUser user) {
        var business = businesses
                .findById(user.businessId())
                .orElseThrow(() -> new NotFoundException("Business", user.businessId()));
        return readModel.summarise(business.getId(), business.getCurrency(), LocalDate.now(clock));
    }
}
