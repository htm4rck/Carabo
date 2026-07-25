-- Carabo: GRE (Guía de Remisión Electrónica) + API Key per tenant

-- API Key for tenant authentication
ALTER TABLE carabo.tenant ADD COLUMN IF NOT EXISTS api_key VARCHAR(64) UNIQUE;
CREATE INDEX IF NOT EXISTS idx_tenant_api_key ON carabo.tenant(api_key);

-- Dispatch Guide (GRE 2.0)
CREATE TABLE IF NOT EXISTS carabo.dispatch_guide (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_code           VARCHAR(50) NOT NULL,
    series                  VARCHAR(10) NOT NULL,
    number                  INTEGER NOT NULL,
    issue_date              DATE NOT NULL,
    start_date              DATE NOT NULL,
    issuer_ruc              VARCHAR(11) NOT NULL,
    issuer_name             VARCHAR(300) NOT NULL,
    receiver_doc_type       VARCHAR(2) NOT NULL,
    receiver_doc            VARCHAR(20) NOT NULL,
    receiver_name           VARCHAR(300) NOT NULL,
    transport_mode          VARCHAR(2) NOT NULL,
    gross_weight            NUMERIC(18,3),
    weight_unit             VARCHAR(5) DEFAULT 'KGM',
    package_count           INTEGER,
    origin_ubigeo           VARCHAR(6),
    origin_address          VARCHAR(500),
    destination_ubigeo      VARCHAR(6),
    destination_address     VARCHAR(500),
    carrier_ruc             VARCHAR(11),
    carrier_name            VARCHAR(300),
    driver_doc_type         VARCHAR(2),
    driver_doc              VARCHAR(20),
    driver_name             VARCHAR(300),
    driver_license          VARCHAR(20),
    vehicle_plate           VARCHAR(10),
    related_doc_type        VARCHAR(5),
    related_doc_number      VARCHAR(30),
    transfer_reason_code    VARCHAR(2),
    transfer_reason_desc    VARCHAR(200),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    hash_code               VARCHAR(100),
    sunat_response          TEXT,
    callback_url            VARCHAR(500),
    external_id             VARCHAR(100),
    created_at              TIMESTAMP DEFAULT NOW(),
    updated_at              TIMESTAMP DEFAULT NOW(),
    UNIQUE(business_code, series, number)
);

CREATE TABLE IF NOT EXISTS carabo.dispatch_guide_item (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispatch_guide_id   UUID NOT NULL REFERENCES carabo.dispatch_guide(id),
    line_number         INTEGER NOT NULL,
    product_code        VARCHAR(50),
    product_name        VARCHAR(500) NOT NULL,
    unit_code           VARCHAR(10) DEFAULT 'NIU',
    quantity            NUMERIC(18,4) NOT NULL,
    weight              NUMERIC(18,3)
);

CREATE INDEX IF NOT EXISTS idx_gre_business ON carabo.dispatch_guide(business_code, status);
CREATE INDEX IF NOT EXISTS idx_gre_item_guide ON carabo.dispatch_guide_item(dispatch_guide_id);
