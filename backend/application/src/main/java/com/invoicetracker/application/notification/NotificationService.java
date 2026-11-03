package com.invoicetracker.application.notification;

import com.invoicetracker.application.common.CurrentUser;
import com.invoicetracker.application.common.NotFoundException;
import com.invoicetracker.application.common.PageResult;
import com.invoicetracker.application.common.Paging;
import com.invoicetracker.application.port.NotificationRepository;
import com.invoicetracker.domain.notification.Notification;
import com.invoicetracker.domain.notification.NotificationChannel;
import com.invoicetracker.domain.notification.NotificationKind;
import com.invoicetracker.domain.notification.NotificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The business user's view of the notification outbox (the in-app notification centre). */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationView> list(CurrentUser user, boolean unreadOnly, Paging paging) {
        return notifications
                .findByBusiness(user.businessId(), unreadOnly, paging)
                .map(NotificationView::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(CurrentUser user) {
        return notifications.countUnread(user.businessId());
    }

    @Transactional
    public NotificationView markRead(CurrentUser user, UUID id) {
        Notification notification = notifications
                .findByIdAndBusinessId(id, user.businessId())
                .orElseThrow(() -> new NotFoundException("Notification", id));
        notification.markRead(clock.instant());
        return NotificationView.from(notifications.save(notification));
    }

    @Transactional
    public int markAllRead(CurrentUser user) {
        return notifications.markAllRead(user.businessId(), clock.instant());
    }

    public record NotificationView(
            UUID id,
            NotificationKind kind,
            String stage,
            NotificationChannel channel,
            String recipient,
            String subject,
            String body,
            NotificationStatus status,
            int attempts,
            String lastError,
            UUID invoiceId,
            String invoiceNumber,
            Instant createdAt,
            Instant sentAt,
            Instant readAt) {

        static NotificationView from(Notification n) {
            return new NotificationView(
                    n.getId(),
                    n.getKind(),
                    n.getStage(),
                    n.getChannel(),
                    n.getRecipient(),
                    n.getSubject(),
                    n.getBody(),
                    n.getStatus(),
                    n.getAttempts(),
                    n.getLastError(),
                    n.getInvoiceId(),
                    n.getInvoiceNumber(),
                    n.getCreatedAt(),
                    n.getSentAt(),
                    n.getReadAt());
        }
    }
}
