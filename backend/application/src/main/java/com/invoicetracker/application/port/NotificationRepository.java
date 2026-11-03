package com.invoicetracker.application.port;

import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    Optional<Notification> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean exists(UUID invoiceId, String stage, NotificationChannel channel);

    /** Newest first. */
    PageResult<Notification> findByBusiness(UUID businessId, boolean unreadOnly, Paging paging);

    long countUnread(UUID businessId);

    int markAllRead(UUID businessId, Instant now);

    /** Oldest pending notifications first (all tenants). */
    List<UUID> findPendingIds(int limit);
}
