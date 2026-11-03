package com.invoicetracker.application.port;

import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.domain.customer.Customer;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    /** Case-insensitive search on name and email, ordered by name. */
    PageResult<Customer> search(UUID businessId, String search, Paging paging);

    Customer save(Customer customer);

    void delete(Customer customer);
}
