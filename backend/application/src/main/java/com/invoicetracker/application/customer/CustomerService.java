package com.invoicetracker.application.customer;

import com.invoicetracker.application.common.AddressDto;
import com.invoicetracker.application.common.ConflictException;
import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.application.common.Text;
import com.invoicetracker.application.port.CustomerRepository;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.domain.customer.Customer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, InvoiceRepository invoices, Clock clock) {
        this.customers = customers;
        this.invoices = invoices;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<CustomerDto> list(CurrentUser user, String search, Paging paging) {
        return customers.search(user.businessId(), Text.clean(search), paging).map(CustomerDto::from);
    }

    @Transactional(readOnly = true)
    public CustomerDto get(CurrentUser user, UUID id) {
        return CustomerDto.from(load(user, id));
    }

    @Transactional
    public CustomerDto create(CurrentUser user, CustomerCommand command) {
        Customer customer = Customer.create(
                user.businessId(),
                Text.clean(command.name()),
                Text.clean(command.email()),
                Text.clean(command.phone()),
                AddressDto.toDomain(command.billingAddress()),
                clock.instant());
        return CustomerDto.from(customers.save(customer));
    }

    @Transactional
    public CustomerDto update(CurrentUser user, UUID id, CustomerCommand command) {
        Customer customer = load(user, id);
        customer.update(
                Text.clean(command.name()),
                Text.clean(command.email()),
                Text.clean(command.phone()),
                AddressDto.toDomain(command.billingAddress()),
                clock.instant());
        return CustomerDto.from(customers.save(customer));
    }

    @Transactional
    public void delete(CurrentUser user, UUID id) {
        Customer customer = load(user, id);
        if (invoices.existsForCustomer(user.businessId(), id)) {
            throw new ConflictException("customer_has_invoices", "This customer has invoices and cannot be deleted");
        }
        customers.delete(customer);
    }

    private Customer load(CurrentUser user, UUID id) {
        return customers
                .findByIdAndBusinessId(id, user.businessId())
                .orElseThrow(() -> new NotFoundException("Customer", id));
    }

    public record CustomerCommand(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 30) @Pattern(regexp = "^[+0-9 ()\\-]*$", message = "must be a valid phone number") String phone,
            @Valid AddressDto billingAddress) {}

    public record CustomerDto(
            UUID id, String name, String email, String phone, AddressDto billingAddress, Instant createdAt) {

        public static CustomerDto from(Customer c) {
            return new CustomerDto(
                    c.getId(),
                    c.getName(),
                    c.getEmail(),
                    c.getPhone(),
                    AddressDto.from(c.getBillingAddress()),
                    c.getCreatedAt());
        }
    }
}
