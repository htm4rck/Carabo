package io.carabo.core.country.co;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.DocumentTax;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Colombia DIAN UBL 2.1 XML builder.
 * Based on DIAN Technical Annex v1.9 for electronic invoicing.
 * Supports: Factura Electrónica (01), Nota Crédito (91), Nota Débito (92).
 */
public class DianUblBuilder {

    private static final String INV_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CN_NS = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    private static final String DN_NS = "urn:oasis:names:specification:ubl:schema:xsd:DebitNote-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String STS = "dian:gov:co:facturaelectronica:Structures-2-1";

    public String build(Document doc) {
        return switch (doc.getDocType()) {
            case INVOICE, RECEIPT -> buildInvoice(doc);
            case CREDIT_NOTE -> buildCreditNote(doc);
            case DEBIT_NOTE -> buildDebitNote(doc);
            default -> throw new IllegalArgumentException("Unsupported doc type for DIAN: " + doc.getDocType());
        };
    }

    private String buildInvoice(Document doc) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Invoice xmlns=\"").append(INV_NS).append("\"\n");
        appendNamespaces(xml);
        xml.append(">\n");

        appendExtensions(xml);
        xml.append("  <cbc:UBLVersionID>UBL 2.1</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>").append(doc.getDocType().sunatCode().equals("01") ? "10" : "20").append("</cbc:CustomizationID>\n");
        xml.append("  <cbc:ProfileID>DIAN 2.1</cbc:ProfileID>\n");
        xml.append("  <cbc:ProfileExecutionID>2</cbc:ProfileExecutionID>\n"); // 1=prod, 2=test
        xml.append("  <cbc:ID>").append(doc.getFullNumber()).append("</cbc:ID>\n");
        xml.append("  <cbc:UUID schemeID=\"2\" schemeName=\"CUFE-SHA384\"></cbc:UUID>\n");
        xml.append("  <cbc:IssueDate>").append(doc.getIssueDate()).append("</cbc:IssueDate>\n");
        xml.append("  <cbc:IssueTime>").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("-05:00</cbc:IssueTime>\n");
        xml.append("  <cbc:InvoiceTypeCode>01</cbc:InvoiceTypeCode>\n");
        xml.append("  <cbc:DocumentCurrencyCode>").append(doc.getCurrency()).append("</cbc:DocumentCurrencyCode>\n");
        xml.append("  <cbc:LineCountNumeric>").append(doc.getLines().size()).append("</cbc:LineCountNumeric>\n");

        appendSupplier(xml, doc);
        appendCustomer(xml, doc);
        appendTaxTotal(xml, doc, "IVA", "01");
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
        xml.append("  <cbc:UBLVersionID>UBL 2.1</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>20</cbc:CustomizationID>\n");
        xml.append("  <cbc:ProfileID>DIAN 2.1</cbc:ProfileID>\n");
        xml.append("  <cbc:ID>").append(doc.getFullNumber()).append("</cbc:ID>\n");
        xml.append("  <cbc:IssueDate>").append(doc.getIssueDate()).append("</cbc:IssueDate>\n");
        xml.append("  <cbc:DocumentCurrencyCode>").append(doc.getCurrency()).append("</cbc:DocumentCurrencyCode>\n");

        if (doc.getRefSeries() != null) {
            xml.append("  <cac:BillingReference>\n");
            xml.append("    <cac:InvoiceDocumentReference>\n");
            xml.append("      <cbc:ID>").append(doc.getRefSeries()).append("-").append(String.format("%08d", doc.getRefNumber())).append("</cbc:ID>\n");
            xml.append("      <cbc:UUID schemeName=\"CUFE-SHA384\"></cbc:UUID>\n");
            xml.append("    </cac:InvoiceDocumentReference>\n");
            xml.append("  </cac:BillingReference>\n");
        }

        appendSupplier(xml, doc);
        appendCustomer(xml, doc);
        appendTaxTotal(xml, doc, "IVA", "01");
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
        xml.append("  <cbc:UBLVersionID>UBL 2.1</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>20</cbc:CustomizationID>\n");
        xml.append("  <cbc:ProfileID>DIAN 2.1</cbc:ProfileID>\n");
        xml.append("  <cbc:ID>").append(doc.getFullNumber()).append("</cbc:ID>\n");
        xml.append("  <cbc:IssueDate>").append(doc.getIssueDate()).append("</cbc:IssueDate>\n");
        xml.append("  <cbc:DocumentCurrencyCode>").append(doc.getCurrency()).append("</cbc:DocumentCurrencyCode>\n");

        if (doc.getRefSeries() != null) {
            xml.append("  <cac:BillingReference>\n");
            xml.append("    <cac:InvoiceDocumentReference>\n");
            xml.append("      <cbc:ID>").append(doc.getRefSeries()).append("-").append(String.format("%08d", doc.getRefNumber())).append("</cbc:ID>\n");
            xml.append("    </cac:InvoiceDocumentReference>\n");
            xml.append("  </cac:BillingReference>\n");
        }

        appendSupplier(xml, doc);
        appendCustomer(xml, doc);
        appendTaxTotal(xml, doc, "IVA", "01");
        appendLegalMonetaryTotal(xml, doc);
        appendLines(xml, doc, "DebitNoteLine");

        xml.append("</DebitNote>");
        return xml.toString();
    }

    private void appendNamespaces(StringBuilder xml) {
        xml.append("  xmlns:cac=\"").append(CAC).append("\"\n");
        xml.append("  xmlns:cbc=\"").append(CBC).append("\"\n");
        xml.append("  xmlns:ext=\"").append(EXT).append("\"\n");
        xml.append("  xmlns:sts=\"").append(STS).append("\"");
    }

    private void appendExtensions(StringBuilder xml) {
        xml.append("  <ext:UBLExtensions>\n");
        xml.append("    <ext:UBLExtension><ext:ExtensionContent>\n");
        xml.append("      <sts:DianExtensions>\n");
        xml.append("        <sts:InvoiceControl>\n");
        xml.append("          <sts:InvoiceAuthorization>0</sts:InvoiceAuthorization>\n");
        xml.append("          <sts:AuthorizationPeriod><cbc:StartDate>2024-01-01</cbc:StartDate><cbc:EndDate>2025-12-31</cbc:EndDate></sts:AuthorizationPeriod>\n");
        xml.append("          <sts:AuthorizedInvoices><sts:Prefix>SETT</sts:Prefix><sts:From>1</sts:From><sts:To>99999999</sts:To></sts:AuthorizedInvoices>\n");
        xml.append("        </sts:InvoiceControl>\n");
        xml.append("        <sts:InvoiceSource><cbc:IdentificationCode>CO</cbc:IdentificationCode></sts:InvoiceSource>\n");
        xml.append("        <sts:SoftwareProvider>\n");
        xml.append("          <sts:ProviderID schemeID=\"4\">900000000</sts:ProviderID>\n");
        xml.append("          <sts:SoftwareID>0</sts:SoftwareID>\n");
        xml.append("        </sts:SoftwareProvider>\n");
        xml.append("        <sts:SoftwareSecurityCode>0</sts:SoftwareSecurityCode>\n");
        xml.append("      </sts:DianExtensions>\n");
        xml.append("    </ext:ExtensionContent></ext:UBLExtension>\n");
        xml.append("    <ext:UBLExtension><ext:ExtensionContent></ext:ExtensionContent></ext:UBLExtension>\n");
        xml.append("  </ext:UBLExtensions>\n");
    }

    private void appendSupplier(StringBuilder xml, Document doc) {
        xml.append("  <cac:AccountingSupplierParty>\n");
        xml.append("    <cbc:AdditionalAccountID>1</cbc:AdditionalAccountID>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cac:PartyIdentification><cbc:ID schemeID=\"31\">").append(doc.getIssuerRuc()).append("</cbc:ID></cac:PartyIdentification>\n");
        xml.append("      <cac:PartyName><cbc:Name><![CDATA[").append(doc.getIssuerName()).append("]]></cbc:Name></cac:PartyName>\n");
        xml.append("      <cac:PartyTaxScheme>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(doc.getIssuerName()).append("]]></cbc:RegistrationName>\n");
        xml.append("        <cbc:CompanyID schemeID=\"31\">").append(doc.getIssuerRuc()).append("</cbc:CompanyID>\n");
        xml.append("        <cac:TaxScheme><cbc:ID>01</cbc:ID><cbc:Name>IVA</cbc:Name></cac:TaxScheme>\n");
        xml.append("      </cac:PartyTaxScheme>\n");
        xml.append("      <cac:PartyLegalEntity>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(doc.getIssuerName()).append("]]></cbc:RegistrationName>\n");
        xml.append("        <cbc:CompanyID schemeID=\"31\">").append(doc.getIssuerRuc()).append("</cbc:CompanyID>\n");
        xml.append("      </cac:PartyLegalEntity>\n");
        if (doc.getIssuerAddress() != null) {
            xml.append("      <cac:PhysicalLocation><cac:Address><cac:AddressLine><cbc:Line><![CDATA[").append(doc.getIssuerAddress()).append("]]></cbc:Line></cac:AddressLine></cac:Address></cac:PhysicalLocation>\n");
        }
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:AccountingSupplierParty>\n");
    }

    private void appendCustomer(StringBuilder xml, Document doc) {
        xml.append("  <cac:AccountingCustomerParty>\n");
        xml.append("    <cbc:AdditionalAccountID>2</cbc:AdditionalAccountID>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cac:PartyIdentification><cbc:ID schemeID=\"").append(mapDocTypeScheme(doc.getReceiverDocType())).append("\">").append(doc.getReceiverDoc()).append("</cbc:ID></cac:PartyIdentification>\n");
        xml.append("      <cac:PartyTaxScheme>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(doc.getReceiverName()).append("]]></cbc:RegistrationName>\n");
        xml.append("        <cbc:CompanyID schemeID=\"").append(mapDocTypeScheme(doc.getReceiverDocType())).append("\">").append(doc.getReceiverDoc()).append("</cbc:CompanyID>\n");
        xml.append("        <cac:TaxScheme><cbc:ID>ZZ</cbc:ID><cbc:Name>No aplica</cbc:Name></cac:TaxScheme>\n");
        xml.append("      </cac:PartyTaxScheme>\n");
        xml.append("      <cac:PartyLegalEntity>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(doc.getReceiverName()).append("]]></cbc:RegistrationName>\n");
        xml.append("      </cac:PartyLegalEntity>\n");
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:AccountingCustomerParty>\n");
    }

    private void appendTaxTotal(StringBuilder xml, Document doc, String taxName, String taxId) {
        xml.append("  <cac:TaxTotal>\n");
        xml.append("    <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getIgvAmount()).append("</cbc:TaxAmount>\n");
        xml.append("    <cac:TaxSubtotal>\n");
        xml.append("      <cbc:TaxableAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTaxBase()).append("</cbc:TaxableAmount>\n");
        xml.append("      <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getIgvAmount()).append("</cbc:TaxAmount>\n");
        xml.append("      <cac:TaxCategory><cbc:Percent>19.00</cbc:Percent>\n");
        xml.append("        <cac:TaxScheme><cbc:ID>").append(taxId).append("</cbc:ID><cbc:Name>").append(taxName).append("</cbc:Name></cac:TaxScheme>\n");
        xml.append("      </cac:TaxCategory>\n");
        xml.append("    </cac:TaxSubtotal>\n");
        xml.append("  </cac:TaxTotal>\n");
    }

    private void appendLegalMonetaryTotal(StringBuilder xml, Document doc) {
        xml.append("  <cac:LegalMonetaryTotal>\n");
        xml.append("    <cbc:LineExtensionAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTaxBase()).append("</cbc:LineExtensionAmount>\n");
        xml.append("    <cbc:TaxExclusiveAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTaxBase()).append("</cbc:TaxExclusiveAmount>\n");
        xml.append("    <cbc:TaxInclusiveAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTotal()).append("</cbc:TaxInclusiveAmount>\n");
        xml.append("    <cbc:PayableAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTotal()).append("</cbc:PayableAmount>\n");
        xml.append("  </cac:LegalMonetaryTotal>\n");
    }

    private void appendLines(StringBuilder xml, Document doc, String lineTag) {
        for (DocumentLine line : doc.getLines()) {
            xml.append("  <cac:").append(lineTag).append(">\n");
            xml.append("    <cbc:ID>").append(line.getLineNumber()).append("</cbc:ID>\n");
            xml.append("    <cbc:InvoicedQuantity unitCode=\"").append(line.getUnitCode()).append("\">").append(line.getQuantity()).append("</cbc:InvoicedQuantity>\n");
            xml.append("    <cbc:LineExtensionAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(line.getTaxBase()).append("</cbc:LineExtensionAmount>\n");
            xml.append("    <cac:TaxTotal>\n");
            xml.append("      <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(line.getIgvAmount()).append("</cbc:TaxAmount>\n");
            xml.append("      <cac:TaxSubtotal>\n");
            xml.append("        <cbc:TaxableAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(line.getTaxBase()).append("</cbc:TaxableAmount>\n");
            xml.append("        <cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(line.getIgvAmount()).append("</cbc:TaxAmount>\n");
            xml.append("        <cac:TaxCategory><cbc:Percent>19.00</cbc:Percent>\n");
            xml.append("          <cac:TaxScheme><cbc:ID>01</cbc:ID><cbc:Name>IVA</cbc:Name></cac:TaxScheme>\n");
            xml.append("        </cac:TaxCategory>\n");
            xml.append("      </cac:TaxSubtotal>\n");
            xml.append("    </cac:TaxTotal>\n");
            xml.append("    <cac:Item><cbc:Description><![CDATA[").append(line.getProductName()).append("]]></cbc:Description></cac:Item>\n");
            xml.append("    <cac:Price><cbc:PriceAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(line.getUnitPrice()).append("</cbc:PriceAmount></cac:Price>\n");
            xml.append("  </cac:").append(lineTag).append(">\n");
        }
    }

    private String mapDocTypeScheme(String docType) {
        return switch (docType) {
            case "6" -> "31"; // NIT
            case "1" -> "13"; // Cédula
            case "4" -> "22"; // CE
            default -> "31";
        };
    }
}
