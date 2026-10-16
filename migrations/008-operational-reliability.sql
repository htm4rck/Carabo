-- Durable retries, notification outbox and persisted RC/RA operations.

ALTER TABLE carabo.document
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

ALTER TABLE carabo.dispatch_guide
    ADD COLUMN IF NOT EXISTS sunat_ticket VARCHAR(100),
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_document_retry_due
    ON carabo.document (next_retry_at)
    WHERE status IN ('PENDING', 'ERROR', 'VALIDATED');

CREATE TABLE IF NOT EXISTS carabo.notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_key VARCHAR(200) NOT NULL UNIQUE,
    business_code VARCHAR(50) NOT NULL,
    document_id UUID NOT NULL REFERENCES carabo.document(id),
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('CALLBACK', 'WEBHOOK')),
    target_url VARCHAR(500) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RETRY', 'DELIVERED', 'DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_due
    ON carabo.notification_outbox (next_attempt_at)
    WHERE status IN ('PENDING', 'RETRY');
CREATE INDEX IF NOT EXISTS idx_notification_outbox_tenant
    ON carabo.notification_outbox (business_code, created_at DESC);

CREATE TABLE IF NOT EXISTS carabo.batch_sequence (
    business_code VARCHAR(50) NOT NULL,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('RC', 'RA')),
    issue_date DATE NOT NULL,
    last_number INTEGER NOT NULL CHECK (last_number > 0),
    PRIMARY KEY (business_code, kind, issue_date)
);

CREATE TABLE IF NOT EXISTS carabo.batch_operation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_code VARCHAR(50) NOT NULL,
    kind VARCHAR(5) NOT NULL CHECK (kind IN ('RC', 'RA')),
    identifier VARCHAR(100) NOT NULL,
    ticket VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    message TEXT,
    reference_date DATE,
    document_count INTEGER NOT NULL DEFAULT 0,
    cdr_xml TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_code, identifier)
);

CREATE INDEX IF NOT EXISTS idx_batch_operation_tenant
    ON carabo.batch_operation (business_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_batch_operation_ticket
    ON carabo.batch_operation (business_code, ticket) WHERE ticket IS NOT NULL;
