-- Carabo: Facturación Electrónica (Multitenant)
-- Schema + tables for document management

CREATE SCHEMA IF NOT EXISTS carabo;

-- Tenant: configuración por negocio
CREATE TABLE carabo.tenant (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_code       VARCHAR(50) NOT NULL UNIQUE,
    business_name       VARCHAR(300),
    issuer_ruc          VARCHAR(11) NOT NULL,
    issuer_name         VARCHAR(300) NOT NULL,
    issuer_trade        VARCHAR(300),
    issuer_address      VARCHAR(500),
    issuer_ubigeo       VARCHAR(6),
    send_mode           VARCHAR(20) NOT NULL DEFAULT 'SUNAT_DIRECT',
    sunat_endpoint      VARCHAR(500),
    sol_user            VARCHAR(100),
    sol_password        VARCHAR(100),
    ose_base_url        VARCHAR(500),
    ose_api_token       VARCHAR(500),
    certificate_file    VARCHAR(200),
    certificate_password VARCHAR(200),
    certificate_valid_from DATE,
    certificate_valid_to DATE,
    environment         VARCHAR(20) DEFAULT 'BETA',
    webhook_url         VARCHAR(500),
    webhook_secret      VARCHAR(200),
    invoice_series      VARCHAR(10) DEFAULT 'F001',
    receipt_series      VARCHAR(10) DEFAULT 'B001',
    credit_note_series  VARCHAR(10) DEFAULT 'FC01',
    debit_note_series   VARCHAR(10) DEFAULT 'FD01',
    max_documents_per_day INTEGER DEFAULT 1000,
    country_code        VARCHAR(3) DEFAULT 'PE',
    active              BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE carabo.document (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_code   VARCHAR(50) NOT NULL,
    doc_type        VARCHAR(20) NOT NULL,
    series          VARCHAR(10) NOT NULL,
    number          INTEGER NOT NULL,
    issue_date      DATE NOT NULL,
    due_date        DATE,
    currency        VARCHAR(3) DEFAULT 'PEN',
    issuer_ruc      VARCHAR(11) NOT NULL,
    issuer_name     VARCHAR(300) NOT NULL,
    issuer_trade    VARCHAR(300),
    issuer_address  VARCHAR(500),
    issuer_ubigeo   VARCHAR(6),
    receiver_doc_type VARCHAR(2) NOT NULL,
    receiver_doc    VARCHAR(20) NOT NULL,
    receiver_name   VARCHAR(300) NOT NULL,
    receiver_address VARCHAR(500),
    tax_base        NUMERIC(18,2) NOT NULL DEFAULT 0,
    igv_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    isc_amount      NUMERIC(18,2) DEFAULT 0,
    other_taxes     NUMERIC(18,2) DEFAULT 0,
    discount_total  NUMERIC(18,2) DEFAULT 0,
    total           NUMERIC(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    send_mode       VARCHAR(20) DEFAULT 'SUNAT_DIRECT',
    hash_code       VARCHAR(100),
    sunat_ticket    VARCHAR(100),
    sunat_response  TEXT,
    retry_count     INTEGER DEFAULT 0,
    ref_doc_type    VARCHAR(5),
    ref_series      VARCHAR(10),
    ref_number      INTEGER,
    ref_reason      VARCHAR(500),
    callback_url    VARCHAR(500),
    external_id     VARCHAR(100),
    source_doc_id   VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(business_code, series, number)
);

CREATE INDEX idx_doc_source ON carabo.document(business_code, external_id);

CREATE TABLE carabo.document_line (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES carabo.document(id),
    line_number     INTEGER NOT NULL,
    product_code    VARCHAR(50),
    product_name    VARCHAR(500) NOT NULL,
    unit_code       VARCHAR(10) DEFAULT 'NIU',
    quantity        NUMERIC(18,4) NOT NULL,
    unit_price      NUMERIC(18,6) NOT NULL,
    gross_price     NUMERIC(18,6),
    discount        NUMERIC(18,2) DEFAULT 0,
    tax_base        NUMERIC(18,2) NOT NULL,
    igv_amount      NUMERIC(18,2) NOT NULL,
    igv_rate        NUMERIC(5,2) DEFAULT 18.00,
    affectation_code VARCHAR(5) DEFAULT '10',
    line_total      NUMERIC(18,2) NOT NULL
);

CREATE TABLE carabo.document_tax (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES carabo.document(id),
    tax_code        VARCHAR(10) NOT NULL,
    tax_name        VARCHAR(50) NOT NULL,
    tax_base        NUMERIC(18,2) NOT NULL,
    tax_amount      NUMERIC(18,2) NOT NULL,
    tax_rate        NUMERIC(5,2)
);

CREATE TABLE carabo.document_send_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES carabo.document(id),
    attempt         INTEGER NOT NULL DEFAULT 1,
    send_mode       VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    ticket          VARCHAR(100),
    response_code   VARCHAR(10),
    response_msg    TEXT,
    cdr_xml         TEXT,
    sent_at         TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_doc_business_status ON carabo.document(business_code, status);
CREATE INDEX idx_doc_external ON carabo.document(business_code, external_id);
CREATE INDEX idx_doc_line_doc ON carabo.document_line(document_id);
CREATE INDEX idx_doc_tax_doc ON carabo.document_tax(document_id);
CREATE INDEX idx_doc_send_log_doc ON carabo.document_send_log(document_id);
