-- Carabo: Seed electronic document templates into tordo.layout (setup.pdf_template_base)
-- These templates are used by Carabo via HTTP call to tordo.layout for PDF generation

INSERT INTO setup.pdf_template_base (id, template_code, template_name, template_type, page_format, html_content, is_default, description, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'ELECTRONIC_INVOICE_A4', 'Factura Electrónica A4', 'ELECTRONIC_INVOICE', 'A4',
   '<!-- LOADED FROM FILE: templates/electronic-invoice-a4.hbs.html -->',
   true, 'Template A4 para facturas y notas de crédito/débito electrónicas con QR SUNAT', NOW(), NOW()),

  (gen_random_uuid(), 'ELECTRONIC_RECEIPT_TICKET', 'Boleta Electrónica Ticket', 'ELECTRONIC_RECEIPT', 'TICKET_80MM',
   '<!-- LOADED FROM FILE: templates/electronic-receipt-ticket.hbs.html -->',
   true, 'Template ticket 80mm para boletas electrónicas con QR SUNAT', NOW(), NOW()),

  (gen_random_uuid(), 'ELECTRONIC_CREDIT_NOTE_A4', 'Nota de Crédito Electrónica A4', 'ELECTRONIC_CREDIT_NOTE', 'A4',
   '<!-- REUSES ELECTRONIC_INVOICE_A4 template with reference section -->',
   true, 'Template A4 para notas de crédito electrónicas', NOW(), NOW()),

  (gen_random_uuid(), 'ELECTRONIC_DEBIT_NOTE_A4', 'Nota de Débito Electrónica A4', 'ELECTRONIC_DEBIT_NOTE', 'A4',
   '<!-- REUSES ELECTRONIC_INVOICE_A4 template with reference section -->',
   true, 'Template A4 para notas de débito electrónicas', NOW(), NOW()),

  (gen_random_uuid(), 'ELECTRONIC_DISPATCH_GUIDE_A4', 'Guía de Remisión Electrónica A4', 'ELECTRONIC_DISPATCH_GUIDE', 'A4',
   '<!-- TO BE IMPLEMENTED: GRE template -->',
   true, 'Template A4 para guías de remisión electrónicas', NOW(), NOW())

ON CONFLICT (template_code) DO NOTHING;
