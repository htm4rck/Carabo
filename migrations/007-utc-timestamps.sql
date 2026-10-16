-- Persist technical/audit dates as absolute UTC instants.
-- Existing TIMESTAMP values were produced by UTC-configured Carabo runtimes,
-- so they are interpreted explicitly as UTC during conversion.

ALTER TABLE carabo.tenant
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN api_key_rotated_at TYPE TIMESTAMPTZ USING api_key_rotated_at AT TIME ZONE 'UTC';

ALTER TABLE carabo.document
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE carabo.document_send_log
    ALTER COLUMN sent_at TYPE TIMESTAMPTZ USING sent_at AT TIME ZONE 'UTC';

ALTER TABLE carabo.dispatch_guide
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE carabo.document_sequence
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE carabo.tenant
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE carabo.document
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE carabo.document_send_log
    ALTER COLUMN sent_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE carabo.dispatch_guide
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE carabo.document_sequence
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;
