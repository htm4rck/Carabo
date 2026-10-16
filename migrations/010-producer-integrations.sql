-- Independent machine identities for external document producers.
CREATE TABLE IF NOT EXISTS carabo.producer_integration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_code VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    api_key_hash VARCHAR(64) NOT NULL UNIQUE,
    scopes VARCHAR(200) NOT NULL DEFAULT 'DOCUMENT_INGEST,DOCUMENT_READ',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rotated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    last_http_status INTEGER,
    total_requests BIGINT NOT NULL DEFAULT 0,
    failure_requests BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_producer_integration_code UNIQUE (business_code, code),
    CONSTRAINT fk_producer_integration_tenant FOREIGN KEY (business_code)
        REFERENCES carabo.tenant (business_code) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_producer_integration_tenant
    ON carabo.producer_integration (business_code, status, updated_at DESC);

-- Preserve existing tenant keys as an explicit transitional producer.
INSERT INTO carabo.producer_integration
    (business_code, code, name, description, api_key_hash, scopes, status, rotated_at)
SELECT business_code, 'legacy-tenant', 'Credencial heredada',
       'Compatibilidad temporal; crea una credencial independiente por aplicación productora.',
       api_key_hash, 'DOCUMENT_INGEST,DOCUMENT_READ,DOCUMENT_EMIT', 'ACTIVE',
       COALESCE(api_key_rotated_at, CURRENT_TIMESTAMP)
FROM carabo.tenant
WHERE api_key_hash IS NOT NULL
ON CONFLICT (business_code, code) DO NOTHING;

ALTER TABLE carabo.document
    ADD COLUMN IF NOT EXISTS producer_id UUID,
    ADD COLUMN IF NOT EXISTS producer_code VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_document_producer
    ON carabo.document (business_code, producer_code, created_at DESC);

ALTER TABLE carabo.async_message_audit
    ADD COLUMN IF NOT EXISTS producer_id UUID,
    ADD COLUMN IF NOT EXISTS producer_code VARCHAR(50);
