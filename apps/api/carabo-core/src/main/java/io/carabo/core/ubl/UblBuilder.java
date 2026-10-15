package io.carabo.core.ubl;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.DocumentTax;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.validation.PeruTaxRules;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Generates UBL 2.1 XML compliant with SUNAT Peru electronic invoicing.
 * Supports: Invoice (01), Receipt/Boleta (03), CreditNote (07), DebitNote (08).
 */
public class UblBuilder {

    private static final String UBL_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CN_NS = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    private static final String DN_NS = "urn:oasis:names:specification:ubl:schema:xsd:DebitNote-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String SUNAT_NS = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public String build(Document doc) {
        return switch (doc.getDocType()) {
            case INVOICE, RECEIPT -> buildInvoice(doc);
            case CREDIT_NOTE -> buildCreditNote(doc);
            case DEBIT_NOTE -> buildDebitNote(doc);
            default -> throw new IllegalArgumentException("Unsupported doc type for UBL: " + doc.getDocType());
        };
    }

    private String buildInvoice(Document doc) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Invoice xmlns=\"").append(UBL_NS).append("\"\n");
        appendNamespaces(xml);
        xml.append(">\n");

        appendExtensions(xml);
        appendUblVersion(xml);
        appendId(xml, doc);
        appendIssueDate(xml, doc);
        appendDueDate(xml, doc);
        appendInvoiceTypeCode(xml, doc);
        appendCurrency(xml, doc);
        appendSignature(xml, doc);
        appendSupplier(xml, doc);
        appendCustomer(xml, doc);
        appendTaxTotal(xml, doc);
        appendLegalMonetaryTotal(xml, doc);
        appendLines(xml, doc, "InvoiceLine");

        xml.append("</Invoice>");
        return xml.toString();
    }

    private String buildCreditNote(Document doc) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<CreditNote xmlns=\"").append(CN_NS).append("\"\n");
        appendNamespaces(xml);
        xml.append(">\n");

        appendExtensions(xml);
        appendUblVersion(xml);
        appendId(xml, doc);
        appendIssueDate(xml, doc);
        appendCurrency(xml, doc);

        // Discrepancy response
        xml.append("  <cac:DiscrepancyResponse>\n");
        xml.append("    <cbc:ReferenceID>").append(doc.getRefSeries()).append("-")
            .append(String.format("%08d", doc.getRefNumber())).append("</cbc:ReferenceID>\n");
        xml.append("    <cbc:ResponseCode>01</cbc:ResponseCode>\n"); // 01=Anulación
        xml.append("    <cbc:Description>").append(escapeXml(doc.getRefReason())).append("</cbc:Description>\n");
        xml.append("  </cac:DiscrepancyResponse>\n");

        // Billing reference
        xml.append("  <cac:BillingReference>\n");
        xml.append("    <cac:InvoiceDocumentReference>\n");
        xml.append("      <cbc:ID>").append(doc.getRefSeries()).append("-")
            .append(String.format("%08d", doc.getRefNumber())).append("</cbc:ID>\n");
        xml.append("      <cbc:DocumentTypeCode>").append(doc.getRefDocType()).append("</cbc:DocumentTypeCode>\n");
        xml.append("    </cac:InvoiceDocumentReference>\n");
        xml.append("  </cac:BillingReference>\n");

        appendSignature(xml, doc);
        appendSupplier(xml, doc);
        appendCustomer(xml, doc);
        appendTaxTotal(xml, doc);
        appendLegalMonetaryTotal(xml, doc);
        appendLines(xml, doc, "CreditNoteLine");

        xml.append("</CreditNote>");
        return xml.toString();
    }

    private String buildDebitNote(Document doc) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<DebitNote xmlns=\"").append(DN_NS).append("\"\n");
        appendNamespaces(xml);
        xml.append(">\n");

        appendExtensions(xml);
        appendUblVersion(xml);
        appendId(xml, doc);
        appendIssueDate(xml, doc);
        appendCurrency(xml, doc);

        // Discrepancy response
        xml.append("  <cac:DiscrepancyResponse>\n");
        xml.append("    <cbc:ReferenceID>").append(doc.getRefSeries()).append("-")
            .append(String.format("%08d", doc.getRefNumber())).append("</cbc:ReferenceID>\n");
        xml.append("    <cbc:ResponseCode>01</cbc:ResponseCode>\n");
        xml.append("    <cbc:Description>").append(escapeXml(doc.getRefReason())).append("</cbc:Description>\n");
        xml.append("  </cac:DiscrepancyResponse>\n");

        // Billing reference
        xml.append("  <cac:BillingReference>\n");
        xml.append("    <cac:InvoiceDocumentReference>\n");
        xml.append("      <cbc:ID>").append(doc.getRefSeries()).append("-")
            .append(String.format("%08d", doc.getRefNumber())).append("</cbc:ID>\n");
        xml.append("      <cbc:DocumentTypeCode>").append(doc.getRefDocType()).append("</cbc:DocumentTypeCode>\n");
        xml.append("    </cac:InvoiceDocumentReference>\n");
        xml.append("  </cac:BillingReference>\n");

        appendSignature(xml, doc);
        appendSupplier(xml, doc);
        appendCustomer(xml, doc);
        appendTaxTotal(xml, doc);
        appendLegalMonetaryTotal(xml, doc);
        appendLines(xml, doc, "DebitNoteLine");

        xml.append("</DebitNote>");
        return xml.toString();
    }

    // --- Building blocks ---

    private void appendNamespaces(StringBuilder xml) {
        xml.append("  xmlns:cac=\"").append(CAC).append("\"\n");
        xml.append("  xmlns:cbc=\"").append(CBC).append("\"\n");
        xml.append("  xmlns:ext=\"").append(EXT).append("\"\n");
        xml.append("  xmlns:ds=\"").append(DS).append("\"\n");
        xml.append("  xmlns:sac=\"").append(SUNAT_NS).append("\"");
    }

    private void appendExtensions(StringBuilder xml) {
        xml.append("  <ext:UBLExtensions>\n");
        xml.append("    <ext:UBLExtension>\n");
        xml.append("      <ext:ExtensionContent>\n");
        xml.append("        <!-- Signature placeholder -->\n");
        xml.append("      </ext:ExtensionContent>\n");
        xml.append("    </ext:UBLExtension>\n");
        xml.append("  </ext:UBLExtensions>\n");
    }

    private void appendUblVersion(StringBuilder xml) {
        xml.append("  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>2.0</cbc:CustomizationID>\n");
    }

    private void appendId(StringBuilder xml, Document doc) {
        xml.append("  <cbc:ID>").append(doc.getFullNumber()).append("</cbc:ID>\n");
    }

    private void appendIssueDate(StringBuilder xml, Document doc) {
        xml.append("  <cbc:IssueDate>").append(doc.getIssueDate().format(DATE_FMT)).append("</cbc:IssueDate>\n");
    }

    private void appendDueDate(StringBuilder xml, Document doc) {
        if (doc.getDueDate() != null) {
            xml.append("  <cbc:DueDate>").append(doc.getDueDate().format(DATE_FMT)).append("</cbc:DueDate>\n");
        }
    }

    private void appendInvoiceTypeCode(StringBuilder xml, Document doc) {
        xml.append("  <cbc:InvoiceTypeCode listID=\"0101\">")
            .append(doc.getDocType().sunatCode()).append("</cbc:InvoiceTypeCode>\n");
    }

    private void appendCurrency(StringBuilder xml, Document doc) {
        xml.append("  <cbc:DocumentCurrencyCode>").append(doc.getCurrency()).append("</cbc:DocumentCurrencyCode>\n");
    }

    private void appendSignature(StringBuilder xml, Document doc) {
        xml.append("  <cac:Signature>\n");
        xml.append("    <cbc:ID>IDSign").append(doc.getIssuerRuc()).append("</cbc:ID>\n");
        xml.append("    <cac:SignatoryParty>\n");
        xml.append("      <cac:PartyIdentification><cbc:ID>").append(doc.getIssuerRuc()).append("</cbc:ID></cac:PartyIdentification>\n");
        xml.append("      <cac:PartyName><cbc:Name>").append(escapeXml(doc.getIssuerName())).append("</cbc:Name></cac:PartyName>\n");
        xml.append("    </cac:SignatoryParty>\n");
        xml.append("    <cac:DigitalSignatureAttachment>\n");
        xml.append("      <cac:ExternalReference><cbc:URI>#SignatureValue</cbc:URI></cac:ExternalReference>\n");
        xml.append("    </cac:DigitalSignatureAttachment>\n");
        xml.append("  </cac:Signature>\n");
    }

    private void appendSupplier(StringBuilder xml, Document doc) {
        xml.append("  <cac:AccountingSupplierParty>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cac:PartyIdentification>\n");
        xml.append("        <cbc:ID schemeID=\"6\">").append(doc.getIssuerRuc()).append("</cbc:ID>\n");
        xml.append("      </cac:PartyIdentification>\n");
        xml.append("      <cac:PartyName><cbc:Name><![CDATA[").append(doc.getIssuerTrade() != null ? doc.getIssuerTrade() : doc.getIssuerName()).append("]]></cbc:Name></cac:PartyName>\n");
        xml.append("      <cac:PartyLegalEntity>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(doc.getIssuerName()).append("]]></cbc:RegistrationName>\n");
        xml.append("        <cac:RegistrationAddress>\n");
        if (doc.getIssuerUbigeo() != null) {
            xml.append("          <cbc:ID>").append(doc.getIssuerUbigeo()).append("</cbc:ID>\n");
        }
        xml.append("          <cbc:AddressTypeCode>0000</cbc:AddressTypeCode>\n");
        if (doc.getIssuerAddress() != null) {
            xml.append("          <cac:AddressLine><cbc:Line><![CDATA[").append(doc.getIssuerAddress()).append("]]></cbc:Line></cac:AddressLine>\n");
        }
        xml.append("        </cac:RegistrationAddress>\n");
        xml.append("      </cac:PartyLegalEntity>\n");
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:AccountingSupplierParty>\n");
    }

    private void appendCustomer(StringBuilder xml, Document doc) {
        xml.append("  <cac:AccountingCustomerParty>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cac:PartyIdentification>\n");
        xml.append("        <cbc:ID schemeID=\"").append(doc.getReceiverDocType()).append("\">")
            .append(doc.getReceiverDoc()).append("</cbc:ID>\n");
        xml.append("      </cac:PartyIdentification>\n");
        xml.append("      <cac:PartyLegalEntity>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(doc.getReceiverName()).append("]]></cbc:RegistrationName>\n");
        if (doc.getReceiverAddress() != null) {
            xml.append("        <cac:RegistrationAddress>\n");
            xml.append("          <cac:AddressLine><cbc:Line><![CDATA[").append(doc.getReceiverAddress()).append("]]></cbc:Line></cac:AddressLine>\n");
            xml.append("        </cac:RegistrationAddress>\n");
        }
        xml.append("      </cac:PartyLegalEntity>\n");
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:AccountingCustomerParty>\n");
    }

    private void appendTaxTotal(StringBuilder xml, Document doc) {
        xml.append("  <cac:TaxTotal>\n");
        xml.append("    <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">")
            .append(doc.getIgvAmount().add(doc.getIscAmount() != null ? doc.getIscAmount() : BigDecimal.ZERO)).append("</cbc:TaxAmount>\n");

        for (DocumentTax tax : doc.getTaxes()) {
            var profile = PeruTaxRules.profileByTaxCode(tax.getTaxCode());
            xml.append("    <cac:TaxSubtotal>\n");
            xml.append("      <cbc:TaxableAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(tax.getTaxBase()).append("</cbc:TaxableAmount>\n");
            xml.append("      <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(tax.getTaxAmount()).append("</cbc:TaxAmount>\n");
            xml.append("      <cac:TaxCategory>\n");
            xml.append("        <cbc:ID schemeID=\"UN/ECE 5305\">")
                .append(profile != null ? profile.categoryCode() : "S").append("</cbc:ID>\n");
            xml.append("        <cbc:Percent>").append(tax.getTaxRate()).append("</cbc:Percent>\n");
            xml.append("        <cac:TaxScheme>\n");
            xml.append("          <cbc:ID>").append(tax.getTaxCode()).append("</cbc:ID>\n");
            xml.append("          <cbc:Name>").append(tax.getTaxName()).append("</cbc:Name>\n");
            xml.append("          <cbc:TaxTypeCode>")
                .append(profile != null ? profile.taxTypeCode() : "VAT").append("</cbc:TaxTypeCode>\n");
            xml.append("        </cac:TaxScheme>\n");
            xml.append("      </cac:TaxCategory>\n");
            xml.append("    </cac:TaxSubtotal>\n");
        }

        xml.append("  </cac:TaxTotal>\n");
    }

    private void appendLegalMonetaryTotal(StringBuilder xml, Document doc) {
        xml.append("  <cac:LegalMonetaryTotal>\n");
        xml.append("    <cbc:LineExtensionAmount currencyID=\"").append(doc.getCurrency()).append("\">")
            .append(doc.getTaxBase()).append("</cbc:LineExtensionAmount>\n");
        xml.append("    <cbc:TaxInclusiveAmount currencyID=\"").append(doc.getCurrency()).append("\">")
            .append(doc.getTotal()).append("</cbc:TaxInclusiveAmount>\n");
        if (doc.getDiscountTotal() != null && doc.getDiscountTotal().compareTo(BigDecimal.ZERO) > 0) {
            xml.append("    <cbc:AllowanceTotalAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(doc.getDiscountTotal()).append("</cbc:AllowanceTotalAmount>\n");
        }
        xml.append("    <cbc:PayableAmount currencyID=\"").append(doc.getCurrency()).append("\">")
            .append(doc.getTotal()).append("</cbc:PayableAmount>\n");
        xml.append("  </cac:LegalMonetaryTotal>\n");
    }

    private void appendLines(StringBuilder xml, Document doc, String lineTag) {
        for (DocumentLine line : doc.getLines()) {
            var profile = PeruTaxRules.profile(line.getAffectationCode());
            xml.append("  <cac:").append(lineTag).append(">\n");
            xml.append("    <cbc:ID>").append(line.getLineNumber()).append("</cbc:ID>\n");
            xml.append("    <cbc:InvoicedQuantity unitCode=\"").append(line.getUnitCode()).append("\">")
                .append(line.getQuantity()).append("</cbc:InvoicedQuantity>\n");
            xml.append("    <cbc:LineExtensionAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(line.getTaxBase()).append("</cbc:LineExtensionAmount>\n");

            // Pricing
            xml.append("    <cac:PricingReference>\n");
            xml.append("      <cac:AlternativeConditionPrice>\n");
            xml.append("        <cbc:PriceAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(line.getGrossPrice()).append("</cbc:PriceAmount>\n");
            xml.append("        <cbc:PriceTypeCode>01</cbc:PriceTypeCode>\n");
            xml.append("      </cac:AlternativeConditionPrice>\n");
            xml.append("    </cac:PricingReference>\n");

            // Tax
            xml.append("    <cac:TaxTotal>\n");
            xml.append("      <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(line.getIgvAmount()).append("</cbc:TaxAmount>\n");
            xml.append("      <cac:TaxSubtotal>\n");
            xml.append("        <cbc:TaxableAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(line.getTaxBase()).append("</cbc:TaxableAmount>\n");
            xml.append("        <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(line.getIgvAmount()).append("</cbc:TaxAmount>\n");
            xml.append("        <cac:TaxCategory>\n");
            xml.append("          <cbc:ID schemeID=\"UN/ECE 5305\">")
                .append(profile != null ? profile.categoryCode() : "S").append("</cbc:ID>\n");
            xml.append("          <cbc:Percent>").append(line.getIgvRate()).append("</cbc:Percent>\n");
            xml.append("          <cbc:TaxExemptionReasonCode>").append(line.getAffectationCode()).append("</cbc:TaxExemptionReasonCode>\n");
            xml.append("          <cac:TaxScheme><cbc:ID>").append(profile != null ? profile.taxCode() : "1000")
                .append("</cbc:ID><cbc:Name>").append(profile != null ? profile.taxName() : "IGV")
                .append("</cbc:Name><cbc:TaxTypeCode>").append(profile != null ? profile.taxTypeCode() : "VAT")
                .append("</cbc:TaxTypeCode></cac:TaxScheme>\n");
            xml.append("        </cac:TaxCategory>\n");
            xml.append("      </cac:TaxSubtotal>\n");
            xml.append("    </cac:TaxTotal>\n");

            // Item
            xml.append("    <cac:Item>\n");
            xml.append("      <cbc:Description><![CDATA[").append(line.getProductName()).append("]]></cbc:Description>\n");
            if (line.getProductCode() != null) {
                xml.append("      <cac:SellersItemIdentification><cbc:ID>").append(line.getProductCode()).append("</cbc:ID></cac:SellersItemIdentification>\n");
            }
            xml.append("    </cac:Item>\n");

            // Price
            xml.append("    <cac:Price>\n");
            xml.append("      <cbc:PriceAmount currencyID=\"").append(doc.getCurrency()).append("\">")
                .append(line.getUnitPrice()).append("</cbc:PriceAmount>\n");
            xml.append("    </cac:Price>\n");

            xml.append("  </cac:").append(lineTag).append(">\n");
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
