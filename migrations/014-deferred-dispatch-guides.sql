-- A guide intent is durable before configuration and external submission.
ALTER TABLE carabo.dispatch_guide ALTER COLUMN number DROP NOT NULL;
ALTER TABLE carabo.dispatch_guide ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE carabo.dispatch_guide ADD COLUMN IF NOT EXISTS environment VARCHAR(20);
-- Unknown historic environments stay unverified and cannot authorize transport PDFs.
ALTER TABLE carabo.dispatch_guide ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(64);
ALTER TABLE carabo.dispatch_guide ADD COLUMN IF NOT EXISTS sunat_response_code VARCHAR(100);
ALTER TABLE carabo.tenant ADD COLUMN IF NOT EXISTS gre_client_id VARCHAR(200);
ALTER TABLE carabo.tenant ADD COLUMN IF NOT EXISTS gre_client_secret VARCHAR(200);
CREATE INDEX IF NOT EXISTS idx_gre_pending_work ON carabo.dispatch_guide(status,next_retry_at)
    WHERE status IN ('PENDING','BLOCKED_CONFIGURATION','RETRY_PENDING','VALIDATED','ERROR','SENT');
