package com.invoicetracker.infrastructure.persistence;

import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.NotificationStatus;
import com.invoicetracker.domain.user.AppUser;
import com.invoicetracker.domain.user.RefreshToken;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data interfaces. Package-private: the rest of the system only sees the ports. */
final class SpringDataRepositories {

    private SpringDataRepositories() {}

    interface BusinessJpa extends JpaRepository<Business, UUID> {}

    interface UserJpa extends JpaRepository<AppUser, UUID> {
        Optional<AppUser> findByEmail(String email);

        boolean existsByEmail(String email);
    }

    interface RefreshTokenJpa extends JpaRepository<RefreshToken, UUID> {
        Optional<RefreshToken> findByTokenHash(String tokenHash);

        @Modifying(flushAutomatically = true)
        @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
        int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
    }

    interface CustomerJpa extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
        Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);
    }

    interface InvoiceJpa extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

        @EntityGraph(attributePaths = "customer")
        Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId);

        @Override
        @EntityGraph(attributePaths = "customer")
        Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);

        @Query("select count(i) > 0 from Invoice i where i.businessId = :businessId and i.customer.id = :customerId")
        boolean existsForCustomer(@Param("businessId") UUID businessId, @Param("customerId") UUID customerId);

        // The two job queries compare each invoice with its own business's date: the real date
        // plus that business's demo clock offset.
        @Query(
                nativeQuery = true,
                value =
                        """
                        SELECT i.id FROM invoices i JOIN businesses b ON b.id = i.business_id
                        WHERE i.status IN ('SENT', 'PARTIALLY_PAID')
                          AND i.due_date < CAST(:today AS date) + b.clock_offset_days
                        ORDER BY i.due_date
                        """)
        List<UUID> findIdsToMarkOverdue(@Param("today") LocalDate realToday);

        @Query(
                nativeQuery = true,
                value =
                        """
                        SELECT i.id FROM invoices i JOIN businesses b ON b.id = i.business_id
                        WHERE i.status IN ('SENT', 'PARTIALLY_PAID', 'OVERDUE')
                          AND i.due_date <= CAST(:today AS date) + b.clock_offset_days + :leadDays
                        ORDER BY i.due_date
                        """)
        List<UUID> findOpenIdsDueSoonOrLate(@Param("today") LocalDate realToday, @Param("leadDays") int leadDays);

        @Query("select i.id from Invoice i where i.businessId = :businessId and i.status = :status"
                + " and i.dueDate >= :today")
        List<UUID> findIdsByStatusDueOnOrAfter(
                @Param("businessId") UUID businessId,
                @Param("status") InvoiceStatus status,
                @Param("today") LocalDate today);
    }

    interface NotificationJpa extends JpaRepository<Notification, UUID> {
        Optional<Notification> findByIdAndBusinessId(UUID id, UUID businessId);

        boolean existsByInvoiceIdAndStageAndChannel(UUID invoiceId, String stage, NotificationChannel channel);

        Page<Notification> findByBusinessId(UUID businessId, Pageable pageable);

        Page<Notification> findByBusinessIdAndReadAtIsNull(UUID businessId, Pageable pageable);

        long countByBusinessIdAndReadAtIsNull(UUID businessId);

        @Modifying(flushAutomatically = true)
        @Query("update Notification n set n.readAt = :now where n.businessId = :businessId and n.readAt is null")
        int markAllRead(@Param("businessId") UUID businessId, @Param("now") Instant now);

        @Query("select n.id from Notification n where n.status = :status order by n.createdAt")
        List<UUID> findIdsByStatus(@Param("status") NotificationStatus status, Pageable pageable);
    }
}
