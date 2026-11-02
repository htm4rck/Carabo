ALTER TABLE carabo.document ADD COLUMN IF NOT EXISTS receiver_email VARCHAR(254);
ALTER TABLE carabo.notification_outbox ADD COLUMN IF NOT EXISTS lease_id UUID;
ALTER TABLE carabo.notification_outbox ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;
ALTER TABLE carabo.notification_outbox DROP CONSTRAINT IF EXISTS notification_outbox_target_type_check;
ALTER TABLE carabo.notification_outbox ADD CONSTRAINT notification_outbox_target_type_check CHECK (target_type IN ('CALLBACK', 'WEBHOOK', 'CUSTOMER_EMAIL'));
ALTER TABLE carabo.notification_outbox DROP CONSTRAINT IF EXISTS notification_outbox_status_check;
ALTER TABLE carabo.notification_outbox ADD CONSTRAINT notification_outbox_status_check CHECK (status IN ('PENDING', 'RETRY', 'DELIVERED', 'DEAD', 'PROCESSING', 'QUEUED'));
CREATE INDEX IF NOT EXISTS idx_customer_document_mail_due
    ON carabo.notification_outbox (next_attempt_at, id)
    WHERE target_type = 'CUSTOMER_EMAIL' AND status IN ('PENDING', 'RETRY', 'PROCESSING');
