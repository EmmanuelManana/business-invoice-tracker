package com.invoicetracker.infrastructure.persistence;

import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.CustomerRepository;
import com.invoicetracker.application.port.NotificationRepository;
import com.invoicetracker.application.port.RefreshTokenRepository;
import com.invoicetracker.application.port.UserRepository;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.customer.Customer;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.NotificationStatus;
import com.invoicetracker.domain.user.AppUser;
import com.invoicetracker.domain.user.RefreshToken;
import com.invoicetracker.infrastructure.persistence.SpringDataRepositories.BusinessJpa;
import com.invoicetracker.infrastructure.persistence.SpringDataRepositories.CustomerJpa;
import com.invoicetracker.infrastructure.persistence.SpringDataRepositories.NotificationJpa;
import com.invoicetracker.infrastructure.persistence.SpringDataRepositories.RefreshTokenJpa;
import com.invoicetracker.infrastructure.persistence.SpringDataRepositories.UserJpa;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** JPA-backed implementations of the simpler repository ports. */
final class JpaAdapters {

    private JpaAdapters() {}

    @Repository
    static class Businesses implements BusinessRepository {
        private final BusinessJpa jpa;

        Businesses(BusinessJpa jpa) {
            this.jpa = jpa;
        }

        @Override
        public Optional<Business> findById(UUID id) {
            return jpa.findById(id);
        }

        @Override
        public Business save(Business business) {
            return jpa.save(business);
        }
    }

    @Repository
    static class Users implements UserRepository {
        private final UserJpa jpa;

        Users(UserJpa jpa) {
            this.jpa = jpa;
        }

        @Override
        public Optional<AppUser> findById(UUID id) {
            return jpa.findById(id);
        }

        @Override
        public Optional<AppUser> findByEmail(String normalisedEmail) {
            return jpa.findByEmail(normalisedEmail);
        }

        @Override
        public boolean existsByEmail(String normalisedEmail) {
            return jpa.existsByEmail(normalisedEmail);
        }

        @Override
        public AppUser save(AppUser user) {
            return jpa.save(user);
        }
    }

    @Repository
    static class RefreshTokens implements RefreshTokenRepository {
        private final RefreshTokenJpa jpa;

        RefreshTokens(RefreshTokenJpa jpa) {
            this.jpa = jpa;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return jpa.findByTokenHash(tokenHash);
        }

        @Override
        public RefreshToken save(RefreshToken token) {
            return jpa.save(token);
        }

        @Override
        public int revokeAllForUser(UUID userId, Instant now) {
            return jpa.revokeAllForUser(userId, now);
        }
    }

    @Repository
    static class Customers implements CustomerRepository {
        private final CustomerJpa jpa;

        Customers(CustomerJpa jpa) {
            this.jpa = jpa;
        }

        @Override
        public Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId) {
            return jpa.findByIdAndBusinessId(id, businessId);
        }

        @Override
        public PageResult<Customer> search(UUID businessId, String search, Paging paging) {
            Specification<Customer> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("businessId"), businessId));
                if (search != null) {
                    String pattern = Pages.containsPattern(search);
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("name")), pattern, '\\'),
                            cb.like(cb.lower(root.get("email")), pattern, '\\')));
                }
                return cb.and(predicates.toArray(Predicate[]::new));
            };
            var pageable =
                    PageRequest.of(paging.page(), paging.size(), Sort.by("name").and(Sort.by("id")));
            return Pages.toResult(jpa.findAll(spec, pageable));
        }

        @Override
        public Customer save(Customer customer) {
            return jpa.save(customer);
        }

        @Override
        public void delete(Customer customer) {
            jpa.delete(customer);
        }
    }

    @Repository
    static class Notifications implements NotificationRepository {
        private final NotificationJpa jpa;

        Notifications(NotificationJpa jpa) {
            this.jpa = jpa;
        }

        @Override
        public Notification save(Notification notification) {
            return jpa.save(notification);
        }

        @Override
        public Optional<Notification> findById(UUID id) {
            return jpa.findById(id);
        }

        @Override
        public Optional<Notification> findByIdAndBusinessId(UUID id, UUID businessId) {
            return jpa.findByIdAndBusinessId(id, businessId);
        }

        @Override
        public boolean exists(UUID invoiceId, String stage, NotificationChannel channel) {
            return jpa.existsByInvoiceIdAndStageAndChannel(invoiceId, stage, channel);
        }

        @Override
        public PageResult<Notification> findByBusiness(UUID businessId, boolean unreadOnly, Paging paging) {
            var pageable = PageRequest.of(
                    paging.page(), paging.size(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
            return Pages.toResult(
                    unreadOnly
                            ? jpa.findByBusinessIdAndReadAtIsNull(businessId, pageable)
                            : jpa.findByBusinessId(businessId, pageable));
        }

        @Override
        public long countUnread(UUID businessId) {
            return jpa.countByBusinessIdAndReadAtIsNull(businessId);
        }

        @Override
        public int markAllRead(UUID businessId, Instant now) {
            return jpa.markAllRead(businessId, now);
        }

        @Override
        public List<UUID> findPendingIds(int limit) {
            return jpa.findIdsByStatus(NotificationStatus.PENDING, PageRequest.of(0, limit));
        }
    }
}
