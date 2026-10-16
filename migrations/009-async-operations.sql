-- Durable RabbitMQ processing ledger used for governed DLQ replay.
CREATE TABLE IF NOT EXISTS carabo.async_message_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_code VARCHAR(50) NOT NULL,
    external_id VARCHAR(100),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECEIVED','PROCESSED','DEAD','REPLAYED')),
    error_message TEXT,
    delivery_count INTEGER NOT NULL DEFAULT 1,
    replay_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_async_message_tenant_status
    ON carabo.async_message_audit (business_code, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_async_message_external
    ON carabo.async_message_audit (business_code, external_id);
