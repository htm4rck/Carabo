INSERT INTO carabo.document_sequence
    (business_code, document_kind, series, last_number, updated_at)
VALUES ('CONCURRENCY_TEST', 'DOCUMENT', 'F001', 1, NOW())
ON CONFLICT (business_code, document_kind, series) DO UPDATE
SET last_number = carabo.document_sequence.last_number + 1,
    updated_at = NOW()
RETURNING last_number;
