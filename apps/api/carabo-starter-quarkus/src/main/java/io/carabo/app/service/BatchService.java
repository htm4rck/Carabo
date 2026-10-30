package io.carabo.app.service;

import io.carabo.app.security.SecretResolver;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.BatchOperation;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.enums.SendMode;
import io.carabo.core.validation.PeruTaxRules;
import io.carabo.ose.OseClient;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.persistence.LockModeType;
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
import java.time.Instant;
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

    @Inject
    FiscalClock fiscalClock;
    @Inject IdempotencyService idempotencyService;

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
            return recordResult(businessCode, "RC", emptyIdentifier("RC", date), false, null,
                "EMPTY", "No hay boletas para resumir en " + date, 0, date, null);
        }

        LocalDate issueDate = fiscalClock.today();
        String identifier = tenant.getIssuerRuc() + "-RC-" + issueDate.format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + nextBatchNumber(businessCode, "RC", issueDate);
        String xml = buildSummaryXml(tenant, boletas, date, identifier);

        try {
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signed = signer.sign(xml);

            SunatBillService sunat = new SunatBillService(
                sunatEndpoint(tenant), tenant.getSolUser(), secretResolver.resolve(tenant.getSolPassword()));
            String ticket = sunat.sendSummary(identifier, signed.signedXml());

            return recordResult(businessCode, "RC", identifier, true, ticket, "SENT",
                "Resumen enviado, ticket: " + ticket, boletas.size(), date, null);
        } catch (Exception e) {
            LOG.error("Daily summary failed for " + businessCode, e);
            return recordResult(businessCode, "RC", identifier, false, null, "ERROR",
                safeErrorMessage(e), boletas.size(), date, null);
        }
    }

    /**
     * Sends a voided communication (RA) for the specified documents.
     */
    @Transactional
    public BatchResult sendVoidedCommunication(String businessCode, List<UUID> documentIds, String reason) {
        return sendVoidedCommunication(businessCode, documentIds, reason, null);
    }

    @Transactional
    public BatchResult sendVoidedCommunication(String businessCode, List<UUID> documentIds, String reason, String requestKey) {
        if (documentIds == null || documentIds.isEmpty() || documentIds.size() > 100 || documentIds.stream().anyMatch(java.util.Objects::isNull)
            || documentIds.stream().distinct().count() != documentIds.size() || reason == null || reason.isBlank() || reason.length() > 500)
            throw new BadRequestException("Baja requiere IDs únicos y motivo de hasta 500 caracteres");
        List<UUID> ids = documentIds.stream().sorted().toList();
        String key = requestKey == null || requestKey.isBlank() ? "void-" + hash(ids.toString()) : requestKey.trim();
        if (key.length() > 100) throw new BadRequestException("Idempotency-Key excede 100 caracteres");
        String fingerprint = hash(ids + ":" + reason.trim());
        idempotencyService.lock("fiscal-void", businessCode, key);
        BatchOperation prior = em.createQuery("SELECT b FROM BatchOperation b WHERE b.businessCode=:bc AND b.requestKey=:key", BatchOperation.class)
            .setParameter("bc", businessCode).setParameter("key", key).getResultStream().findFirst().orElse(null);
        if (prior != null && !fingerprint.equals(prior.getRequestHash())) throw new ClientErrorException("La llave de baja ya tiene otro contenido",409);
        if (prior != null && !"ERROR".equals(prior.getStatus())) return toResult(prior, !"REJECTED".equals(prior.getStatus()));
        Tenant tenant = tenantService.resolve(businessCode);
        List<Document> docs = em.createQuery(
            "SELECT d FROM Document d WHERE d.id IN :ids AND d.businessCode = :bc ORDER BY d.id", Document.class)
            .setParameter("ids", ids)
            .setParameter("bc", businessCode)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
        if (docs.size() != ids.size()) throw new NotFoundException("Todos los documentos deben existir en la empresa autenticada");
        LocalDate referenceDate = docs.get(0).getIssueDate();
        boolean receiptFamily = docs.get(0).getSeries().startsWith("B");
        for (Document doc : docs) {
            if (!(doc.getStatus() == DocumentStatus.ACCEPTED || doc.getStatus() == DocumentStatus.OBSERVED)) throw new ClientErrorException("Sólo documentos fiscalmente aceptados pueden solicitar baja",409);
            if (!referenceDate.equals(doc.getIssueDate()) || receiptFamily != doc.getSeries().startsWith("B")) throw new BadRequestException("La baja debe agrupar la misma fecha y familia fiscal F/B");
        }
        LocalDate today = fiscalClock.today();
        String kind = receiptFamily ? "RC" : "RA";
        String identifier = prior != null ? prior.getIdentifier() : tenant.getIssuerRuc() + "-" + kind + "-" + today.format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + nextBatchNumber(businessCode, kind, today);
        BatchOperation operation = prior != null ? prior : new BatchOperation();
        operation.setBusinessCode(businessCode); operation.setKind(kind); operation.setIdentifier(identifier);
        operation.setRequestKey(key); operation.setRequestHash(fingerprint); operation.setDocumentCount(docs.size());
        operation.setDocumentIds(ids.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));
        operation.setReferenceDate(referenceDate); operation.setStatus("PROCESSING");
        if (prior == null) em.persist(operation);
        em.flush();
        String xml = receiptFamily ? buildSummaryXml(tenant, docs, referenceDate, identifier).replace("<cbc:ConditionCode>1</cbc:ConditionCode>", "<cbc:ConditionCode>3</cbc:ConditionCode>")
            : buildVoidedXml(tenant, docs, today, identifier, reason.trim());
        try {
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signed = signer.sign(xml);
            String ticket;
            boolean accepted = false;
            if (tenant.getSendMode() == SendMode.OSE) {
                var result = new OseClient(tenant.getOseBaseUrl(), secretResolver.resolve(tenant.getOseApiToken())).send(identifier, signed.signedXml());
                ticket = result.externalId(); accepted = result.accepted();
                operation.setStatus(result.processing() ? "SENT" : accepted ? "ACCEPTED" : result.retryable() ? "ERROR" : "REJECTED");
                operation.setMessage(result.responseMsg()); operation.setCdrXml(result.cdrXml());
            } else {
                SunatBillService sunat = new SunatBillService(sunatEndpoint(tenant), tenant.getSolUser(), secretResolver.resolve(tenant.getSolPassword()));
                ticket = sunat.sendSummary(identifier, signed.signedXml()); operation.setStatus("SENT");
                operation.setMessage("Baja enviada; pendiente de aceptación fiscal");
            }
            operation.setTicket(ticket);
            for (Document doc : docs) {
                if (accepted || "SENT".equals(operation.getStatus())) {
                    doc.setStatus(accepted ? DocumentStatus.VOIDED : DocumentStatus.VOID_REQUESTED);
                    doc.setSunatTicket(ticket); em.merge(doc);
                }
            }
            return toResult(operation, !java.util.Set.of("ERROR","REJECTED").contains(operation.getStatus()));
        } catch (Exception e) {
            LOG.error("Voided communication failed for " + businessCode, e);
            operation.setStatus("ERROR"); operation.setMessage(safeErrorMessage(e));
            return toResult(operation,false);
        }
    }

    /**
     * Checks the status of a previously sent summary/voided ticket.
     */
    @Transactional
    public BatchResult checkTicket(String businessCode, String ticket) {
        Tenant tenant = tenantService.resolve(businessCode);
        BatchOperation operation = em.createQuery(
                "SELECT b FROM BatchOperation b WHERE b.businessCode = :bc AND b.ticket = :ticket ORDER BY b.createdAt DESC",
                BatchOperation.class)
            .setParameter("bc", businessCode).setParameter("ticket", ticket).setMaxResults(1)
            .getResultStream().findFirst().orElse(null);
        if (operation != null) {
            if (java.util.Set.of("ACCEPTED","OBSERVED","REJECTED").contains(operation.getStatus())) return toResult(operation,!"REJECTED".equals(operation.getStatus()));
            String status, message, cdr; boolean accepted;
            if (tenant.getSendMode() == SendMode.OSE) {
                var result = new OseClient(tenant.getOseBaseUrl(),secretResolver.resolve(tenant.getOseApiToken())).getStatus(ticket);
                accepted = result.accepted(); status = result.processing()?"PENDING":accepted?(result.observed()?"OBSERVED":"ACCEPTED"):result.retryable()?"ERROR":"REJECTED";
                message = result.responseMsg(); cdr = result.cdrXml();
            } else {
                var result = new SunatBillService(sunatEndpoint(tenant),tenant.getSolUser(),secretResolver.resolve(tenant.getSolPassword())).getStatus(ticket);
                accepted = result.accepted(); status = result.processing()?"PENDING":accepted?(result.observed()?"OBSERVED":"ACCEPTED"):result.retryable()?"ERROR":"REJECTED";
                message = result.responseMsg(); cdr = result.cdrXml();
            }
            operation.setStatus(status);
            operation.setMessage(message); operation.setCdrXml(cdr);
            if (operation.getDocumentIds() != null && (accepted || "REJECTED".equals(status))) {
                for (String id : operation.getDocumentIds().split(",")) {
                    Document doc = em.find(Document.class,UUID.fromString(id),LockModeType.PESSIMISTIC_WRITE);
                    if (doc != null && businessCode.equals(doc.getBusinessCode()) && doc.getStatus()==DocumentStatus.VOID_REQUESTED)
                        doc.setStatus(accepted?DocumentStatus.VOIDED:DocumentStatus.ACCEPTED);
                }
            }
            em.merge(operation);
            return toResult(operation, accepted);
        }
        throw new NotFoundException("Ticket no encontrado en la empresa autenticada");
    }

    public List<BatchResult> listOperations(String businessCode, String kind, int limit) {
        String jpql = "SELECT b FROM BatchOperation b WHERE b.businessCode = :bc"
            + (kind != null && !kind.isBlank() ? " AND b.kind = :kind" : "") + " ORDER BY b.createdAt DESC";
        var query = em.createQuery(jpql, BatchOperation.class).setParameter("bc", businessCode)
            .setMaxResults(Math.min(Math.max(limit, 1), 200));
        if (kind != null && !kind.isBlank()) query.setParameter("kind", kind.toUpperCase());
        return query.getResultList().stream().map(operation -> toResult(operation,
            "SENT".equals(operation.getStatus()) || "ACCEPTED".equals(operation.getStatus())
                || "OBSERVED".equals(operation.getStatus()))).toList();
    }

    public java.util.Optional<String> getCdr(UUID operationId, String businessCode) {
        return em.createQuery(
                "SELECT b FROM BatchOperation b WHERE b.id = :id AND b.businessCode = :bc", BatchOperation.class)
            .setParameter("id", operationId).setParameter("bc", businessCode)
            .getResultStream().findFirst().map(BatchOperation::getCdrXml)
            .filter(value -> value != null && !value.isBlank());
    }

    // --- XML builders for batch documents ---

    String buildSummaryXml(Tenant tenant, List<Document> boletas, LocalDate date, String identifier) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<SummaryDocuments xmlns=\"urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1\"\n");
        xml.append("  xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"\n");
        xml.append("  xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"\n");
        xml.append("  xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\">\n");
        xml.append("  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>\n");
        xml.append("  <cbc:UBLVersionID>2.0</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>1.1</cbc:CustomizationID>\n");
        // The transport filename includes the issuer RUC; the UBL batch ID does not.
        xml.append("  <cbc:ID>").append(identifier.substring(tenant.getIssuerRuc().length() + 1)).append("</cbc:ID>\n");
        xml.append("  <cbc:ReferenceDate>").append(date).append("</cbc:ReferenceDate>\n");
        xml.append("  <cbc:IssueDate>").append(fiscalClock.today()).append("</cbc:IssueDate>\n");
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
            xml.append("    <sac:BillingPayment><cbc:PaidAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getTaxBase()).append("</cbc:PaidAmount><cbc:InstructionID>01</cbc:InstructionID></sac:BillingPayment>\n");
            xml.append("    <cac:TaxTotal><cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getIgvAmount()).append("</cbc:TaxAmount>");
            xml.append("<cac:TaxSubtotal><cbc:TaxAmount currencyID=\"").append(doc.getCurrency()).append("\">").append(doc.getIgvAmount()).append("</cbc:TaxAmount>");
            xml.append("<cac:TaxCategory><cbc:Percent>").append(PeruTaxRules.profileByTaxCode("1000").rate()).append("</cbc:Percent>");
            xml.append("<cac:TaxScheme><cbc:ID>1000</cbc:ID><cbc:Name>IGV</cbc:Name><cbc:TaxTypeCode>VAT</cbc:TaxTypeCode></cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal></cac:TaxTotal>\n");
            xml.append("  </sac:SummaryDocumentsLine>\n");
        }

        xml.append("</SummaryDocuments>");
        return xml.toString();
    }

    String buildVoidedXml(Tenant tenant, List<Document> docs, LocalDate date, String identifier, String reason) {
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
        xml.append("  <cbc:ID>").append(identifier.substring(tenant.getIssuerRuc().length() + 1)).append("</cbc:ID>\n");
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
            xml.append("    <sac:VoidReasonDescription>").append(xmlText(reason != null ? reason : "Anulación")).append("</sac:VoidReasonDescription>\n");
            xml.append("  </sac:VoidedDocumentsLine>\n");
        }

        xml.append("</VoidedDocuments>");
        return xml.toString();
    }

    private XmlSigner createSigner(Tenant tenant) {
        try {
            String certPath = Path.of(certsBasePath, tenant.getCertificateFile()).toString();
            return new XmlSigner(new FileInputStream(certPath), secretResolver.resolve(tenant.getCertificatePassword()),
                tenant.getCertificateAlias());
        } catch (Exception e) {
            throw new RuntimeException("Cannot load certificate for " + tenant.getBusinessCode(), e);
        }
    }

    private String sunatEndpoint(Tenant tenant) {
        if (tenant.getSunatEndpoint() != null && !tenant.getSunatEndpoint().isBlank()) {
            return tenant.getSunatEndpoint();
        }
        return "PRODUCTION".equalsIgnoreCase(tenant.getEnvironment())
            ? SunatBillService.PROD_URL : SunatBillService.BETA_URL;
    }

    private int nextBatchNumber(String businessCode, String kind, LocalDate issueDate) {
        Object value = em.createNativeQuery("""
            INSERT INTO carabo.batch_sequence (business_code, kind, issue_date, last_number)
            VALUES (:bc, :kind, :date, 1)
            ON CONFLICT (business_code, kind, issue_date)
            DO UPDATE SET last_number = carabo.batch_sequence.last_number + 1
            RETURNING last_number
            """).setParameter("bc", businessCode).setParameter("kind", kind)
            .setParameter("date", issueDate).getSingleResult();
        return ((Number) value).intValue();
    }

    private String emptyIdentifier(String kind, LocalDate date) {
        return kind + "-EMPTY-" + date + "-" + UUID.randomUUID();
    }

    private BatchResult recordResult(String businessCode, String kind, String identifier, boolean success,
                                     String ticket, String status, String message, int documentCount,
                                     LocalDate referenceDate, String cdrXml) {
        BatchOperation operation = new BatchOperation();
        operation.setBusinessCode(businessCode);
        operation.setKind(kind);
        operation.setIdentifier(identifier);
        operation.setTicket(ticket);
        operation.setStatus(status);
        operation.setMessage(message);
        operation.setDocumentCount(documentCount);
        operation.setReferenceDate(referenceDate);
        operation.setCdrXml(cdrXml);
        em.persist(operation);
        em.flush();
        return toResult(operation, success);
    }

    private BatchResult toResult(BatchOperation operation, boolean success) {
        return new BatchResult(operation.getId(), operation.getKind(), operation.getIdentifier(), success,
            operation.getTicket(), operation.getStatus(), operation.getMessage(), operation.getDocumentCount(),
            operation.getReferenceDate(), operation.getUpdatedAt() != null ? operation.getUpdatedAt() : fiscalClock.nowUtc(),
            fiscalClock.zoneId(), operation.getDocumentIds() != null);
    }

    private String safeErrorMessage(Exception exception) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
            ? exception.getMessage() : "La operación no pudo completarse";
    }

    private String xmlText(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record BatchResult(UUID id, String kind, String identifier, boolean success, String ticket, String status, String message,
                              int documentCount, LocalDate referenceDate,
                              Instant generatedAt, String timeZone, boolean voidCommunication) {}
}
