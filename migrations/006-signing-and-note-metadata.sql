ALTER TABLE carabo.tenant
    ADD COLUMN IF NOT EXISTS certificate_alias VARCHAR(200);

COMMENT ON COLUMN carabo.tenant.certificate_alias IS
    'Alias explícito de la clave privada X.509 seleccionada al validar el PKCS#12';

ALTER TABLE carabo.document
    ADD COLUMN IF NOT EXISTS ref_reason_code VARCHAR(2);

COMMENT ON COLUMN carabo.document.ref_reason_code IS
    'Código SUNAT del motivo de nota: catálogo 09 (crédito) o 10 (débito)';
