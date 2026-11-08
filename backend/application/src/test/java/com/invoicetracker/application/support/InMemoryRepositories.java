package com.invoicetracker.application.support;

import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.application.invoice.InvoiceSearchCriteria;
import com.invoicetracker.application.port.BusinessRepository;
import com.invoicetracker.application.port.InvoiceRepository;
import com.invoicetracker.application.port.NotificationRepository;
import com.invoicetracker.application.port.RefreshTokenRepository;
import com.invoicetracker.application.port.UserRepository;
import com.invoicetracker.domain.business.Business;
import com.invoicetracker.domain.invoice.Invoice;
import com.invoicetracker.domain.invoice.InvoiceStatus;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.NotificationStatus;
import com.invoicetracker.domain.user.AppUser;
import com.invoicetracker.domain.user.RefreshToken;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Simple in-memory port implementations for unit tests. */
public final class InMemoryRepositories {

    private InMemoryRepositories() {}

    public static class Businesses implements BusinessRepository {
        private final Map<UUID, Business> store = new LinkedHashMap<>();

        @Override
        public Optional<Business> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Business save(Business business) {
            store.put(business.getId(), business);
            return business;
        }
    }

    public static class Invoices implements InvoiceRepository {
        private final Map<UUID, Invoice> store = new LinkedHashMap<>();
        private final Businesses businesses;

        public Invoices() {
            this(null);
        }

        /** With {@code businesses}, the job queries apply each business's clock offset like the SQL does. */
        public Invoices(Businesses businesses) {
            this.businesses = businesses;
        }

        public int count() {
            return store.size();
        }

        private LocalDate todayFor(Invoice invoice, LocalDate realToday) {
            int offset = businesses == null
                    ? 0
                    : businesses
                            .findById(invoice.getBusinessId())
                            .map(Business::getClockOffsetDays)
                            .orElse(0);
            return realToday.plusDays(offset);
        }

        @Override
        public Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId) {
            return findById(id).filter(i -> i.getBusinessId().equals(businessId));
        }

        @Override
        public Optional<Invoice> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public PageResult<Invoice> search(UUID businessId, InvoiceSearchCriteria criteria) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsForCustomer(UUID businessId, UUID customerId) {
            return store.values().stream()
                    .anyMatch(i -> i.getBusinessId().equals(businessId)
                            && i.getCustomer().getId().equals(customerId));
        }

        @Override
        public Invoice save(Invoice invoice) {
            store.put(invoice.getId(), invoice);
            return invoice;
        }

        @Override
        public List<UUID> findIdsToMarkOverdue(LocalDate realToday) {
            return store.values().stream()
                    .filter(i -> i.getStatus() == InvoiceStatus.SENT || i.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                    .filter(i -> i.getDueDate().isBefore(todayFor(i, realToday)))
                    .map(Invoice::getId)
                    .toList();
        }

        @Override
        public List<UUID> findOpenIdsDueSoonOrLate(LocalDate realToday, int leadDays) {
            return store.values().stream()
                    .filter(i -> i.getStatus().isOpen())
                    .filter(i -> !i.getDueDate().isAfter(todayFor(i, realToday).plusDays(leadDays)))
                    .map(Invoice::getId)
                    .toList();
        }

        @Override
        public List<UUID> findOverdueIdsNotYetDue(UUID businessId, LocalDate today) {
            return store.values().stream()
                    .filter(i -> i.getBusinessId().equals(businessId))
                    .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                    .filter(i -> !i.getDueDate().isBefore(today))
                    .map(Invoice::getId)
                    .toList();
        }
    }

    public static class Notifications implements NotificationRepository {
        private final Map<UUID, Notification> store = new LinkedHashMap<>();

        public List<Notification> all() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Notification save(Notification n) {
            boolean duplicate = store.values().stream()
                    .anyMatch(o -> !o.getId().equals(n.getId())
                            && o.getInvoiceId().equals(n.getInvoiceId())
                            && o.getStage().equals(n.getStage())
                            && o.getChannel() == n.getChannel());
            if (duplicate) {
                throw new IllegalStateException("unique constraint violated (invoice, stage, channel)");
            }
            store.put(n.getId(), n);
            return n;
        }

        @Override
        public Optional<Notification> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Notification> findByIdAndBusinessId(UUID id, UUID businessId) {
            return findById(id).filter(n -> n.getBusinessId().equals(businessId));
        }

        @Override
        public boolean exists(UUID invoiceId, String stage, NotificationChannel channel) {
            return store.values().stream()
                    .anyMatch(n -> invoiceId.equals(n.getInvoiceId())
                            && n.getStage().equals(stage)
                            && n.getChannel() == channel);
        }

        @Override
        public PageResult<Notification> findByBusiness(UUID businessId, boolean unreadOnly, Paging paging) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countUnread(UUID businessId) {
            return store.values().stream()
                    .filter(n -> n.getBusinessId().equals(businessId) && !n.isRead())
                    .count();
        }

        @Override
        public int markAllRead(UUID businessId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<UUID> findPendingIds(int limit) {
            return store.values().stream()
                    .filter(n -> n.getStatus() == NotificationStatus.PENDING)
                    .sorted(Comparator.comparing(Notification::getCreatedAt))
                    .limit(limit)
                    .map(Notification::getId)
                    .toList();
        }
    }

    public static class Users implements UserRepository {
        private final Map<UUID, AppUser> store = new LinkedHashMap<>();

        @Override
        public Optional<AppUser> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<AppUser> findByEmail(String email) {
            return store.values().stream()
                    .filter(u -> u.getEmail().equals(email))
                    .findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public AppUser save(AppUser user) {
            store.put(user.getId(), user);
            return user;
        }
    }

    public static class RefreshTokens implements RefreshTokenRepository {
        private final Map<UUID, RefreshToken> store = new LinkedHashMap<>();

        public List<RefreshToken> all() {
            return new ArrayList<>(store.values());
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return store.values().stream()
                    .filter(t -> t.getTokenHash().equals(tokenHash))
                    .findFirst();
        }

        @Override
        public RefreshToken save(RefreshToken token) {
            store.put(token.getId(), token);
            return token;
        }

        @Override
        public int revokeAllForUser(UUID userId, Instant now) {
            int count = 0;
            for (RefreshToken token : store.values()) {
                if (token.getUserId().equals(userId) && !token.isRevoked()) {
                    token.revoke(now, null);
                    count++;
                }
            }
            return count;
        }
    }
}
