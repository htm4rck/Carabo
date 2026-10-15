-- API keys are bearer credentials: store only a one-way SHA-256 fingerprint.
-- Use PostgreSQL's built-in SHA-256 function so this migration is independent
-- from extension schemas and works with currentSchema=carabo.

ALTER TABLE carabo.tenant
    ADD COLUMN IF NOT EXISTS api_key_hash VARCHAR(64);

UPDATE carabo.tenant
SET api_key_hash = ENCODE(SHA256(CONVERT_TO(api_key, 'UTF8')), 'hex')
WHERE api_key IS NOT NULL
  AND api_key_hash IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_api_key_hash
    ON carabo.tenant (api_key_hash)
    WHERE api_key_hash IS NOT NULL;

-- Keep the legacy column during the compatibility window, but erase plaintext.
UPDATE carabo.tenant SET api_key = NULL WHERE api_key IS NOT NULL;

ALTER TABLE carabo.tenant
    ADD COLUMN IF NOT EXISTS api_key_rotated_at TIMESTAMP;
