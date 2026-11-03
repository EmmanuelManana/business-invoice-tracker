package com.invoicetracker.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A notification in the outbox. Rows are created as PENDING by the reminder job and then handed to
 * a channel-specific sender. In this system the senders are simulated (they log instead of
 * delivering), but the outbox shape is what a real email/SMS integration would use.
 *
 * <p>The database enforces uniqueness on (invoice_id, stage, channel) so a reminder can never be
 * generated twice, even if two job instances race.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public static final String OVERDUE_ALERT_STAGE = "OVERDUE";

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID businessId;

    private UUID invoiceId;

    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationKind kind;

    @Column(nullable = false)
    private String stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;
    private Instant readAt;

    @Version
    private Long version;

    protected Notification() {}

    public static Notification queue(
            UUID businessId,
            UUID invoiceId,
            String invoiceNumber,
            NotificationKind kind,
            String stage,
            NotificationChannel channel,
            String recipient,
            String subject,
            String body,
            Instant now) {
        var n = new Notification();
        n.id = UUID.randomUUID();
        n.businessId = businessId;
        n.invoiceId = invoiceId;
        n.invoiceNumber = invoiceNumber;
        n.kind = kind;
        n.stage = stage;
        n.channel = channel;
        n.recipient = recipient;
        n.subject = subject;
        n.body = body;
        n.status = NotificationStatus.PENDING;
        n.createdAt = now;
        return n;
    }

    public void markSent(Instant now) {
        attempts++;
        status = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
    }

    /** Records a failed delivery; gives up (FAILED) once {@code maxAttempts} is reached. */
    public void markFailed(String error, int maxAttempts) {
        attempts++;
        lastError = error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 500));
        status = attempts >= maxAttempts ? NotificationStatus.FAILED : NotificationStatus.PENDING;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getStage() {
        return stage;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
