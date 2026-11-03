package com.invoicetracker.api.web;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.application.customer.CustomerService;
import com.invoicetracker.application.customer.CustomerService.CustomerCommand;
import com.invoicetracker.application.customer.CustomerService.CustomerDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<CustomerDto> list(
            CurrentUser user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(user, search, Paging.of(page, size));
    }

    @GetMapping("/{id}")
    public CustomerDto get(CurrentUser user, @PathVariable UUID id) {
        return service.get(user, id);
    }

    @PostMapping
    public ResponseEntity<CustomerDto> create(CurrentUser user, @Valid @RequestBody CustomerCommand command) {
        CustomerDto created = service.create(user, command);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public CustomerDto update(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody CustomerCommand command) {
        return service.update(user, id, command);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(CurrentUser user, @PathVariable UUID id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
