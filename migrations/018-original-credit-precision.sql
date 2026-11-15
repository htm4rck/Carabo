ALTER TABLE carabo.document_line ALTER COLUMN product_code TYPE varchar(100);
-- Preserve the original ERP's exact unit value through persistence and fiscal retries.
ALTER TABLE carabo.document_line ALTER COLUMN unit_price TYPE numeric(24,10);
ALTER TABLE carabo.document_line ALTER COLUMN gross_price TYPE numeric(24,10);
ALTER TABLE carabo.document_line ALTER COLUMN quantity TYPE numeric(24,6);
-- Rollback requires verifying that no persisted value exceeds the previous 6/6/4 decimal scales.
