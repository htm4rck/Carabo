ALTER TABLE carabo.batch_operation ADD COLUMN IF NOT EXISTS request_key varchar(100);
ALTER TABLE carabo.batch_operation ADD COLUMN IF NOT EXISTS request_hash varchar(64);
ALTER TABLE carabo.batch_operation ADD COLUMN IF NOT EXISTS document_ids text;
CREATE UNIQUE INDEX IF NOT EXISTS batch_operation_request_unique ON carabo.batch_operation(business_code,request_key) WHERE request_key IS NOT NULL;
