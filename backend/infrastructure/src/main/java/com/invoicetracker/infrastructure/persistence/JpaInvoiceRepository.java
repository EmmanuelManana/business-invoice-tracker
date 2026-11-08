package com.invoicetracker.infrastructure.persistence;

import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.invoice.InvoiceSearchCriteria;
import com.invoicetracker.application.invoice.InvoiceSearchCriteria.SortField;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import com.invoicetracker.infrastructure.persistence.SpringDataRepositories.InvoiceJpa;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class JpaInvoiceRepository implements InvoiceRepository {

    private final InvoiceJpa jpa;

    JpaInvoiceRepository(InvoiceJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId) {
        return jpa.findByIdAndBusinessId(id, businessId);
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public PageResult<Invoice> search(UUID businessId, InvoiceSearchCriteria criteria) {
        Sort.Direction direction = criteria.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, property(criteria.sortBy())).and(Sort.by(Sort.Direction.DESC, "number"));
        var pageable =
                PageRequest.of(criteria.paging().page(), criteria.paging().size(), sort);
        return Pages.toResult(jpa.findAll(specification(businessId, criteria), pageable));
    }

    @Override
    public boolean existsForCustomer(UUID businessId, UUID customerId) {
        return jpa.existsForCustomer(businessId, customerId);
    }

    @Override
    public Invoice save(Invoice invoice) {
        return jpa.saveAndFlush(invoice);
    }

    @Override
    public List<UUID> findIdsToMarkOverdue(LocalDate realToday) {
        return jpa.findIdsToMarkOverdue(realToday);
    }

    @Override
    public List<UUID> findOpenIdsDueSoonOrLate(LocalDate realToday, int leadDays) {
        return jpa.findOpenIdsDueSoonOrLate(realToday, leadDays);
    }

    @Override
    public List<UUID> findOverdueIdsNotYetDue(UUID businessId, LocalDate today) {
        return jpa.findIdsByStatusDueOnOrAfter(businessId, InvoiceStatus.OVERDUE, today);
    }

    private static String property(SortField field) {
        return switch (field) {
            case ISSUE_DATE -> "issueDate";
            case DUE_DATE -> "dueDate";
            case NUMBER -> "number";
            case TOTAL -> "total";
            case STATUS -> "status";
            case CUSTOMER -> "customer.name";
        };
    }

    private static Specification<Invoice> specification(UUID businessId, InvoiceSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), businessId));
            if (!c.statuses().isEmpty()) {
                predicates.add(root.get("status").in(c.statuses()));
            }
            if (c.customerId() != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), c.customerId()));
            }
            if (c.issuedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("issueDate"), c.issuedFrom()));
            }
            if (c.issuedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("issueDate"), c.issuedTo()));
            }
            if (c.search() != null && !c.search().isBlank()) {
                String pattern = Pages.containsPattern(c.search().trim());
                Join<Invoice, Customer> customer = root.join("customer", JoinType.INNER);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("number")), pattern, '\\'),
                        cb.like(cb.lower(customer.get("name")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
