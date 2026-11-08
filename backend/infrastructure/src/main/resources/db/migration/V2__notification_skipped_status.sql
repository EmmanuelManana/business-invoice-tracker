-- Notifications that no longer apply (e.g. the invoice was paid before delivery) are skipped, not sent.
ALTER TABLE notifications DROP CONSTRAINT notifications_status_check;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'));
