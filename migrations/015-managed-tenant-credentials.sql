-- Versioned encrypted credentials. The encryption key stays outside PostgreSQL.
CREATE TABLE IF NOT EXISTS carabo.tenant_credential (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES carabo.tenant(id) ON DELETE CASCADE,
    credential_name VARCHAR(40) NOT NULL,
    encrypted_value TEXT NOT NULL,
    updated_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_tenant_credential_name CHECK (credential_name IN ('solPassword','greClientSecret','oseApiToken','certificatePassword'))
);
CREATE INDEX IF NOT EXISTS idx_tenant_credential_owner ON carabo.tenant_credential(tenant_id,credential_name,created_at);
-- Rollback: stop writers, restore tenant references to their prior versions, then drop this table.
