package com.invoicetracker.application.port;

import com.invoicetracker.domain.business.Business;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository {

    Optional<Business> findById(UUID id);

    Business save(Business business);
}
