package io.carabo.app.service;

import io.carabo.app.security.SecretResolver;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.signer.XmlSigner;
import io.carabo.sunat.SunatBillService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BatchService {

    private static final Logger LOG = Logger.getLogger(BatchService.class);

    @Inject
    EntityManager em;

    @Inject
    TenantService tenantService;

    @Inject
    SecretResolver secretResolver;

    @ConfigProperty(name = "carabo.certs.base-path", defaultValue = "/app/certs")
    String certsBasePath;

    /**
     * Generates and sends a daily summary (RC) for all receipts (boletas)
     * issued on the given date for the specified tenant.
     */
    @Transactional
    public BatchResult sendDailySummary(String businessCode, LocalDate date) {
        Tenant tenant = tenantService.resolve(businessCode);

        @SuppressWarnings("unchecked")
        List<Document> boletas = em.createQuery(
            "SELECT d FROM Document d WHERE d.businessCode = :bc AND d.docType = :dt " +
            "AND d.issueDate = :date AND d.status = :st")
            .setParameter("bc", businessCode)
            .setParameter("dt", DocumentType.RECEIPT)
            .setParameter("date", date)
            .setParameter("st", DocumentStatus.ACCEPTED)
            .getResultList();

        if (boletas.isEmpty()) {
            return new BatchResult(false, null, "No hay boletas para resumir en " + date, 0);
        }

        String identifier = tenant.getIssuerRuc() + "-RC-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-1";
        String xml = buildSummaryXml(tenant, boletas, date, identifier);

        try {
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signed = signer.sign(xml);

            SunatBillService sunat = new SunatBillService(
                tenant.getSunatEndpoint(), tenant.getSolUser(), secretResolver.resolve(tenant.getSolPassword()));
            String ticket = sunat.sendSummary(identifier, signed.signedXml());

            return new BatchResult(true, ticket, "Resumen enviado, ticket: " + ticket, boletas.size());
        } catch (Exception e) {
            LOG.error("Daily summary failed for " + businessCode, e);
            return new BatchResult(false, null, e.getMessage(), boletas.size());
        }
    }

    /**
     * Sends a voided communication (RA) for the specified documents.
     */
    @Transactional
    public BatchResult sendVoidedCommunication(String businessCode, List<UUID> documentIds, String reason) {
        Tenant tenant = tenantService.resolve(businessCode);

        @SuppressWarnings("unchecked")
        List<Document> docs = em.createQuery(
            "SELECT d FROM Document d WHERE d.id IN :ids AND d.businessCode = :bc AND d.status = :st")
            .setParameter("ids", documentIds)
            .setParameter("bc", businessCode)
            .setParameter("st", DocumentStatus.ACCEPTED)
            .getResultList();

        if (docs.isEmpty()) {
            return new BatchResult(false, null, "No hay documentos válidos para anular", 0);
        }

        LocalDate today = LocalDate.now();
        String identifier = tenant.getIssuerRuc() + "-RA-" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-1";
        String xml = buildVoidedXml(tenant, docs, today, identifier, reason);

        try {
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signed = signer.sign(xml);

            SunatBillService sunat = new SunatBillService(
                tenant.getSunatEndpoint(), tenant.getSolUser(), secretResolver.resolve(tenant.getSolPassword()));
            String ticket = sunat.sendSummary(identifier, signed.signedXml());

            // Mark documents as VOID_REQUESTED
            for (Document doc : docs) {
                doc.setStatus(DocumentStatus.VOID_REQUESTED);
                doc.setSunatTicket(ticket);
                em.merge(doc);
            }

            return new BatchResult(true, ticket, "Baja enviada, ticket: " + ticket, docs.size());
        } catch (Exception e) {
            LOG.error("Voided communication failed for " + businessCode, e);
            return new BatchResult(false, null, e.getMessage(), docs.size());
        }
    }

    /**
     * Checks the status of a previously sent summary/voided ticket.
     */
    public BatchResult checkTicket(String businessCode, String ticket) {
        Tenant tenant = tenantService.resolve(businessCode);
        SunatBillService sunat = new SunatBillService(
            tenant.getSunatEndpoint(), tenant.getSolUser(), secretResolver.resolve(tenant.getSolPassword()));
        SunatBillService.SendBillResult result = sunat.getStatus(ticket);

        return new BatchResult(result.accepted(), ticket, result.responseMsg(), 0);
    }

    // --- XML builders for batch documents ---

    private String buildSummaryXml(Tenant tenant, List<Document> boletas, LocalDate date, String identifier) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<SummaryDocuments xmlns=\"urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1\"\n");
        xml.append("  xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"\n");
        xml.append("  xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"\n");
        xml.append("  xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">\n");
        xml.append("  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>\n");
        xml.append("  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>1.1</cbc:CustomizationID>\n");
        xml.append("  <cbc:ID>").append(identifier).append("</cbc:ID>\n");
        xml.append("  <cbc:ReferenceDate>").append(date).append("</cbc:ReferenceDate>\n");
        xml.append("  <cbc:IssueDate>").append(LocalDate.now()).append("</cbc:IssueDate>\n");
        xml.append("  <cac:Signature><cbc:ID>IDSign").append(tenant.getIssuerRuc()).append("</cbc:ID>");
        xml.append("<cac:SignatoryParty><cac:PartyIdentification><cbc:ID>").append(tenant.getIssuerRuc()).append("</cbc:ID></cac:PartyIdentification></cac:SignatoryParty>");
        xml.append("<cac:DigitalSignatureAttachment><cac:ExternalReference><cbc:URI>#SignatureValue</cbc:URI></cac:ExternalReference></cac:DigitalSignatureAttachment></cac:Signature>\n");
        xml.append("  <cac:AccountingSupplierParty><cbc:CustomerAssignedAccountID>").append(tenant.getIssuerRuc()).append("</cbc:CustomerAssignedAccountID>");
        xml.append("<cbc:AdditionalAccountID>6</cbc:AdditionalAccountID>");
        xml.append("<cac:Party><cac:PartyLegalEntity><cbc:RegistrationName><![CDATA[").append(tenant.getIssuerName()).append("]]></cbc:RegistrationName></cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>\n");

        int lineId = 1;
        for (Document doc : boletas) {
            xml.append("  <sac:SummaryDocumentsLine xmlns:sac=\"urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1\">\n");
            xml.append("    <cbc:LineID>").append(lineId++).append("</cbc:LineID>\n");
            xml.append("    <cbc:DocumentTypeCode>").append(doc.getDocType().sunatCode()).append("</cbc:DocumentTypeCode>\n");
            xml.append("    <cbc:ID>").append(doc.getFullNumber()).append("</cbc:ID>\n");
            xml.append("    <cac:AccountingCustomerParty><cbc:CustomerAssignedAccountID>").append(doc.getReceiverDoc()).append("</cbc:CustomerAssignedAccountID>");
            xml.append("<cbc:AdditionalAccountID>").append(doc.getReceiverDocType()).append("</cbc:AdditionalAccountID></cac:AccountingCustomerParty>\n");
            xml.append("    <cac:Status><cbc:ConditionCode>1</cbc:ConditionCode></cac:Status>\n");
            xml.append("    <sac:TotalAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTotal()).append("</sac:TotalAmount>\n");
            xml.append("    <sac:BillingPayment><cbc:PaidAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTotal()).append("</cbc:PaidAmount><cbc:InstructionID>01</cbc:InstructionID></sac:BillingPayment>\n");
            xml.append("    <cac:TaxTotal><cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getIgvAmount()).append("</cbc:TaxAmount>");
            xml.append("<cac:TaxSubtotal><cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getIgvAmount()).append("</cbc:TaxAmount>");
            xml.append("<cac:TaxCategory><cac:TaxScheme><cbc:ID>1000</cbc:ID><cbc:Name>IGV</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal></cac:TaxTotal>\n");
            xml.append("  </sac:SummaryDocumentsLine>\n");
        }

        xml.append("</SummaryDocuments>");
        return xml.toString();
    }

    private String buildVoidedXml(Tenant tenant, List<Document> docs, LocalDate date, String identifier, String reason) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<VoidedDocuments xmlns=\"urn:sunat:names:specification:ubl:peru:schema:xsd:VoidedDocuments-1\"\n");
        xml.append("  xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"\n");
        xml.append("  xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"\n");
        xml.append("  xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\"\n");
        xml.append("  xmlns:sac=\"urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1\">\n");
        xml.append("  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>\n");
        xml.append("  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>1.0</cbc:CustomizationID>\n");
        xml.append("  <cbc:ID>").append(identifier).append("</cbc:ID>\n");
        xml.append("  <cbc:ReferenceDate>").append(docs.get(0).getIssueDate()).append("</cbc:ReferenceDate>\n");
        xml.append("  <cbc:IssueDate>").append(date).append("</cbc:IssueDate>\n");
        xml.append("  <cac:Signature><cbc:ID>IDSign").append(tenant.getIssuerRuc()).append("</cbc:ID>");
        xml.append("<cac:SignatoryParty><cac:PartyIdentification><cbc:ID>").append(tenant.getIssuerRuc()).append("</cbc:ID></cac:PartyIdentification></cac:SignatoryParty>");
        xml.append("<cac:DigitalSignatureAttachment><cac:ExternalReference><cbc:URI>#SignatureValue</cbc:URI></cac:ExternalReference></cac:DigitalSignatureAttachment></cac:Signature>\n");
        xml.append("  <cac:AccountingSupplierParty><cbc:CustomerAssignedAccountID>").append(tenant.getIssuerRuc()).append("</cbc:CustomerAssignedAccountID>");
        xml.append("<cbc:AdditionalAccountID>6</cbc:AdditionalAccountID>");
        xml.append("<cac:Party><cac:PartyLegalEntity><cbc:RegistrationName><![CDATA[").append(tenant.getIssuerName()).append("]]></cbc:RegistrationName></cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>\n");

        int lineId = 1;
        for (Document doc : docs) {
            xml.append("  <sac:VoidedDocumentsLine>\n");
            xml.append("    <cbc:LineID>").append(lineId++).append("</cbc:LineID>\n");
            xml.append("    <cbc:DocumentTypeCode>").append(doc.getDocType().sunatCode()).append("</cbc:DocumentTypeCode>\n");
            xml.append("    <sac:DocumentSerialID>").append(doc.getSeries()).append("</sac:DocumentSerialID>\n");
            xml.append("    <sac:DocumentNumberID>").append(doc.getNumber()).append("</sac:DocumentNumberID>\n");
            xml.append("    <sac:VoidReasonDescription>").append(reason != null ? reason : "Anulación").append("</sac:VoidReasonDescription>\n");
            xml.append("  </sac:VoidedDocumentsLine>\n");
        }

        xml.append("</VoidedDocuments>");
        return xml.toString();
    }

    private XmlSigner createSigner(Tenant tenant) {
        try {
            String certPath = Path.of(certsBasePath, tenant.getCertificateFile()).toString();
            return new XmlSigner(new FileInputStream(certPath), secretResolver.resolve(tenant.getCertificatePassword()));
        } catch (Exception e) {
            throw new RuntimeException("Cannot load certificate for " + tenant.getBusinessCode(), e);
        }
    }

    public record BatchResult(boolean success, String ticket, String message, int documentCount) {}
}
