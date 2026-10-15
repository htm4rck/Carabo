-- Atomic correlatives and idempotency guarantees for documents and GREs.

CREATE TABLE IF NOT EXISTS carabo.document_sequence (
    business_code   VARCHAR(50) NOT NULL,
    document_kind  VARCHAR(20) NOT NULL,
    series          VARCHAR(10) NOT NULL,
    last_number     INTEGER NOT NULL CHECK (last_number >= 0),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (business_code, document_kind, series),
    CONSTRAINT ck_document_sequence_kind
        CHECK (document_kind IN ('DOCUMENT', 'DISPATCH_GUIDE'))
);

-- Bootstrap counters without resetting series that already have documents.
INSERT INTO carabo.document_sequence (business_code, document_kind, series, last_number)
SELECT business_code, 'DOCUMENT', series, MAX(number)
FROM carabo.document
GROUP BY business_code, series
ON CONFLICT (business_code, document_kind, series) DO UPDATE
SET last_number = GREATEST(carabo.document_sequence.last_number, EXCLUDED.last_number),
    updated_at = NOW();

INSERT INTO carabo.document_sequence (business_code, document_kind, series, last_number)
SELECT business_code, 'DISPATCH_GUIDE', series, MAX(number)
FROM carabo.dispatch_guide
GROUP BY business_code, series
ON CONFLICT (business_code, document_kind, series) DO UPDATE
SET last_number = GREATEST(carabo.document_sequence.last_number, EXCLUDED.last_number),
    updated_at = NOW();

-- Empty values do not represent an idempotency key. Non-empty keys are unique
-- inside a tenant, preventing duplicate emission even if an application-level
-- guard is accidentally bypassed.
UPDATE carabo.document
SET external_id = NULLIF(BTRIM(external_id), ''),
    source_doc_id = NULLIF(BTRIM(source_doc_id), '')
WHERE external_id IS DISTINCT FROM NULLIF(BTRIM(external_id), '')
   OR source_doc_id IS DISTINCT FROM NULLIF(BTRIM(source_doc_id), '');

UPDATE carabo.dispatch_guide
SET external_id = NULLIF(BTRIM(external_id), '')
WHERE external_id IS DISTINCT FROM NULLIF(BTRIM(external_id), '');

DROP INDEX IF EXISTS carabo.idx_doc_source;
DROP INDEX IF EXISTS carabo.idx_doc_external;

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_external_id
    ON carabo.document (business_code, external_id)
    WHERE external_id IS NOT NULL AND BTRIM(external_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_source_doc_id
    ON carabo.document (business_code, source_doc_id)
    WHERE source_doc_id IS NOT NULL AND BTRIM(source_doc_id) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_dispatch_guide_external_id
    ON carabo.dispatch_guide (business_code, external_id)
    WHERE external_id IS NOT NULL AND BTRIM(external_id) <> '';
