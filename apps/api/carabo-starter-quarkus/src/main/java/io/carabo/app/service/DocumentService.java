package io.carabo.app.service;

import io.carabo.app.security.SecretResolver;
import io.carabo.core.dto.EmitDocumentRequest;
import io.carabo.core.dto.EmitDocumentResponse;
import io.carabo.core.entity.*;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.enums.SendMode;
import io.carabo.core.ubl.UblBuilder;
import io.carabo.core.validation.DocumentValidator;
import io.carabo.core.validation.PeruTaxRules;
import io.carabo.ose.OseClient;
import io.carabo.signer.XmlSigner;
import io.carabo.storage.DocumentStorage;
import io.carabo.sunat.SunatBillService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DocumentService {

    private static final Logger LOG = Logger.getLogger(DocumentService.class);
    private static final int MAX_RETRIES = 3;

    @Inject
    EntityManager em;

    @Inject
    TenantService tenantService;

    @Inject
    IdempotencyService idempotencyService;

    @Inject
    SecretResolver secretResolver;

    @Inject
    FiscalClock fiscalClock;

    @Inject
    NotificationService notificationService;

    @Inject
    OperationalMetrics metrics;

    @ConfigProperty(name = "carabo.certs.base-path", defaultValue = "/app/certs")
    String certsBasePath;

    @ConfigProperty(name = "carabo.storage.base-path", defaultValue = "/app/storage/documents")
    String storagePath;

    @ConfigProperty(name = "carabo.storage.encryption-key")
    Optional<String> storageEncryptionKey;

    private final UblBuilder ublBuilder = new UblBuilder();
    private final DocumentValidator validator = new DocumentValidator();

    @Transactional
    public EmitDocumentResponse emit(EmitDocumentRequest request) {
        Tenant tenant = tenantService.resolve(request.getBusinessCode());

        request.setExternalId(IdempotencyService.normalize(request.getExternalId()));
        request.setSourceDocId(IdempotencyService.normalize(request.getSourceDocId()));
        idempotencyService.lock("DOCUMENT", request.getBusinessCode(),
            request.getSourceDocId(), request.getExternalId());

        Document existing = findByIdempotencyKey(
            request.getBusinessCode(), request.getSourceDocId(), request.getExternalId());
        if (existing != null) {
            return EmitDocumentResponse.of(
                existing.getId(), existing.getFullNumber(), existing.getStatus().name(),
                existing.getHashCode(), existing.getSunatResponse(), existing.getExternalId());
        }

        Document doc = buildDocument(request, tenant);

        // Reject only an invalid contract. Fiscal setup is evaluated after the
        // durable intent has been accepted, so a missing certificate never
        // makes Operation/Amazilia lose the document.
        var errors = validator.validateStructure(doc);
        if (!errors.isEmpty()) {
            doc.setSunatResponse(String.join("; ", errors));
            return EmitDocumentResponse.of(
                null, null, "VALIDATION_ERROR",
                null, doc.getSunatResponse(), doc.getExternalId());
        }

        doc.setStatus(DocumentStatus.PENDING);
        em.persist(doc);
        em.flush();

        var readinessErrors = validator.validateFiscalReadiness(tenant);
        if (!readinessErrors.isEmpty()) {
            doc.setStatus(DocumentStatus.BLOCKED_CONFIGURATION);
            doc.setSunatResponse(String.join("; ", readinessErrors));
            em.merge(doc);
            notificationService.enqueueFor(doc, tenant);
            return EmitDocumentResponse.of(doc.getId(), null, doc.getStatus().name(),
                null, doc.getSunatResponse(), doc.getExternalId());
        }

        doc.setStatus(DocumentStatus.VALIDATED);

        try {
            String xml = ublBuilder.build(doc);
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signResult = signer.sign(xml);

            doc.setHashCode(signResult.digestValue());
            getStorage().storeSignedXml(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), signResult.signedXml());

            SendResult result = sendToAuthority(doc, signResult.signedXml(), tenant);

            applySendResult(doc, result);

            if (result.cdrXml() != null) {
                getStorage().storeCdr(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), result.cdrXml());
            }

            logSend(doc, result);
        } catch (Exception e) {
            LOG.error("Emit pipeline failed for " + doc.getFullNumber(), e);
            doc.setStatus(DocumentStatus.ERROR);
            doc.setSunatResponse(e.getMessage());
            scheduleRetry(doc, safeMessage(e));
            logSend(doc, new SendResult(DocumentStatus.ERROR, null, null, null, safeMessage(e), null, true));
        }

        em.merge(doc);

        Tenant t = tenantService.resolve(doc.getBusinessCode());
        notificationService.enqueueFor(doc, t);

        return EmitDocumentResponse.of(
            doc.getId(), doc.getFullNumber(), doc.getStatus().name(),
            doc.getHashCode(), doc.getSunatResponse(), doc.getExternalId()
        );
    }

    @Transactional
    public void retry(UUID documentId) {
        Document doc = em.find(Document.class, documentId);
        if (doc == null) return;
        if (doc.getRetryCount() >= MAX_RETRIES) {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setNextRetryAt(null);
            em.merge(doc);
            return;
        }
        if (doc.getStatus() != DocumentStatus.PENDING
            && doc.getStatus() != DocumentStatus.BLOCKED_CONFIGURATION
            && doc.getStatus() != DocumentStatus.ERROR
            && doc.getStatus() != DocumentStatus.VALIDATED) return;

        Tenant tenant = tenantService.resolve(doc.getBusinessCode());
        var readinessErrors = validator.validateFiscalReadiness(tenant);
        if (!readinessErrors.isEmpty()) {
            doc.setStatus(DocumentStatus.BLOCKED_CONFIGURATION);
            doc.setSunatResponse(String.join("; ", readinessErrors));
            doc.setNextRetryAt(null);
            em.merge(doc);
            notificationService.enqueueFor(doc, tenant);
            return;
        }
        if (doc.getNumber() == null) {
            doc.setStatus(DocumentStatus.REJECTED);
            doc.setSunatResponse("El productor no proporcionó un correlativo fiscal válido.");
            em.merge(doc);
            return;
        }
        doc.setRetryCount(doc.getRetryCount() + 1);
        doc.setLastAttemptAt(Instant.now());
        doc.setNextRetryAt(null);

        try {
            String xml = ublBuilder.build(doc);
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signResult = signer.sign(xml);

            doc.setHashCode(signResult.digestValue());
            getStorage().storeSignedXml(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), signResult.signedXml());

            SendResult result = sendToAuthority(doc, signResult.signedXml(), tenant);

            applySendResult(doc, result);

            if (result.cdrXml() != null) {
                getStorage().storeCdr(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), result.cdrXml());
            }

            logSend(doc, result);
        } catch (Exception e) {
            doc.setStatus(DocumentStatus.ERROR);
            scheduleRetry(doc, safeMessage(e));
            logSend(doc, new SendResult(DocumentStatus.ERROR, null, null, null, safeMessage(e), null, true));
        }

        em.merge(doc);

        notificationService.enqueueFor(doc, tenant);
    }

    public Optional<Document> findById(UUID id) {
        return Optional.ofNullable(em.find(Document.class, id));
    }

    public Optional<Document> findById(UUID id, String businessCode) {
        return em.createQuery(
                "SELECT d FROM Document d WHERE d.id = :id AND d.businessCode = :bc", Document.class)
            .setParameter("id", id)
            .setParameter("bc", businessCode)
            .getResultStream()
            .findFirst();
    }

    public Optional<Document> findByExternalId(String externalId, String businessCode) {
        String normalized = IdempotencyService.normalize(externalId);
        if (normalized == null) return Optional.empty();
        return em.createQuery(
                "SELECT d FROM Document d WHERE d.externalId = :externalId AND d.businessCode = :bc", Document.class)
            .setParameter("externalId", normalized)
            .setParameter("bc", businessCode)
            .getResultStream()
            .findFirst();
    }

    @Transactional
    public Optional<Document> refreshStatus(UUID id, String businessCode) {
        Optional<Document> found = findById(id,businessCode);
        if (found.isEmpty()) return found;
        Document doc = found.get();
        em.lock(doc,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (doc.getSunatTicket()==null || !(doc.getStatus()==DocumentStatus.PENDING || doc.getStatus()==DocumentStatus.SENT)) return found;
        Tenant tenant = tenantService.resolve(businessCode);
        SendResult result;
        if (tenant.getSendMode()==SendMode.OSE) {
            var remote = new OseClient(tenant.getOseBaseUrl(),secretResolver.resolve(tenant.getOseApiToken())).getStatus(doc.getSunatTicket());
            var status = remote.processing()?DocumentStatus.PENDING:remote.accepted()?(remote.observed()?DocumentStatus.OBSERVED:DocumentStatus.ACCEPTED):remote.retryable()?DocumentStatus.ERROR:DocumentStatus.REJECTED;
            result = new SendResult(status,remote.digestValue(),doc.getSunatTicket(),remote.responseCode(),remote.responseMsg(),remote.cdrXml(),remote.retryable());
        } else {
            var remote = new SunatBillService(sunatEndpoint(tenant),tenant.getSolUser(),secretResolver.resolve(tenant.getSolPassword())).getStatus(doc.getSunatTicket());
            var status = remote.processing()?DocumentStatus.PENDING:remote.accepted()?(remote.observed()?DocumentStatus.OBSERVED:DocumentStatus.ACCEPTED):remote.retryable()?DocumentStatus.ERROR:DocumentStatus.REJECTED;
            result = new SendResult(status,null,doc.getSunatTicket(),remote.responseCode(),remote.responseMsg(),remote.cdrXml(),remote.retryable());
        }
        applySendResult(doc,result);
        if (result.cdrXml()!=null) getStorage().storeCdr(businessCode,doc.getSeries(),doc.getNumber(),result.cdrXml());
        logSend(doc,result); notificationService.enqueueFor(doc,tenant);
        return found;
    }

    @Transactional
    public boolean retry(UUID documentId, String businessCode) {
        if (findById(documentId, businessCode).isEmpty()) return false;
        retry(documentId);
        return true;
    }

    public Optional<String> getSignedXml(UUID id) {
        return findById(id).flatMap(doc ->
            getStorage().getSignedXml(doc.getBusinessCode(), doc.getSeries(), doc.getNumber()));
    }

    public Optional<String> getSignedXml(UUID id, String businessCode) {
        return findById(id, businessCode).flatMap(doc ->
            getStorage().getSignedXml(doc.getBusinessCode(), doc.getSeries(), doc.getNumber()));
    }

    public Optional<String> getCdr(UUID id) {
        return findById(id).flatMap(doc ->
            getStorage().getCdr(doc.getBusinessCode(), doc.getSeries(), doc.getNumber()));
    }

    public Optional<String> getCdr(UUID id, String businessCode) {
        return findById(id, businessCode).flatMap(doc ->
            getStorage().getCdr(doc.getBusinessCode(), doc.getSeries(), doc.getNumber()));
    }

    @SuppressWarnings("unchecked")
    public List<Document> findRetryable() {
        return em.createQuery(
            "SELECT d FROM Document d WHERE d.status IN (:statuses) AND d.retryCount < :max " +
                "AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now) ORDER BY d.nextRetryAt")
            .setParameter("statuses", List.of(DocumentStatus.PENDING, DocumentStatus.ERROR, DocumentStatus.RETRY_PENDING, DocumentStatus.VALIDATED))
            .setParameter("max", MAX_RETRIES)
            .setParameter("now", Instant.now())
            .setMaxResults(100)
            .getResultList();
    }

    private Document findByIdempotencyKey(String businessCode, String sourceDocId, String externalId) {
        Document bySource = findByIdentifier(businessCode, "sourceDocId", sourceDocId);
        Document byExternal = findByIdentifier(businessCode, "externalId", externalId);

        if (bySource != null && byExternal != null && !bySource.getId().equals(byExternal.getId())) {
            throw new IllegalArgumentException(
                "sourceDocId y externalId pertenecen a documentos diferentes");
        }
        return bySource != null ? bySource : byExternal;
    }

    private Document findByIdentifier(String businessCode, String field, String value) {
        if (value == null) return null;
        return em.createQuery(
                "SELECT d FROM Document d WHERE d.businessCode = :bc AND d." + field + " = :value",
                Document.class)
            .setParameter("bc", businessCode)
            .setParameter("value", value)
            .getResultStream()
            .findFirst()
            .orElse(null);
    }

    // --- Pipeline steps (multitenant) ---

    private SendResult sendToAuthority(Document doc, String signedXml, Tenant tenant) {
        String fileName = doc.getIssuerRuc() + "-" + doc.getDocType().sunatCode() + "-" + doc.getFullNumber();

        if (tenant.getSendMode() == SendMode.OSE) {
            SendResult result = sendViaOse(fileName, signedXml, tenant);
            metrics.fiscalResult("ose", result.status().name().toLowerCase());
            return result;
        }
        SendResult result = sendViaSunat(fileName, signedXml, tenant);
        metrics.fiscalResult("sunat", result.status().name().toLowerCase());
        return result;
    }

    private SendResult sendViaSunat(String fileName, String signedXml, Tenant tenant) {
        SunatBillService sunat = new SunatBillService(
            sunatEndpoint(tenant), tenant.getSolUser(), secretResolver.resolve(tenant.getSolPassword()));
        SunatBillService.SendBillResult result = sunat.sendBill(fileName, signedXml);
        DocumentStatus status = result.processing() ? DocumentStatus.PENDING
            : result.accepted() ? (result.observed() ? DocumentStatus.OBSERVED : DocumentStatus.ACCEPTED)
            : result.retryable() ? DocumentStatus.ERROR : DocumentStatus.REJECTED;
        return new SendResult(status, null, result.ticket(),
            result.responseCode(), result.responseMsg(), result.cdrXml(), result.retryable());
    }

    private SendResult sendViaOse(String fileName, String signedXml, Tenant tenant) {
        OseClient ose = new OseClient(tenant.getOseBaseUrl(), secretResolver.resolve(tenant.getOseApiToken()));
        OseClient.OseResult result = ose.send(fileName, signedXml);
        DocumentStatus status = result.processing() ? DocumentStatus.PENDING
            : result.accepted() ? (result.observed() ? DocumentStatus.OBSERVED : DocumentStatus.ACCEPTED)
            : result.retryable() ? DocumentStatus.ERROR : DocumentStatus.REJECTED;
        return new SendResult(status, result.digestValue(), result.externalId(),
            result.responseCode(), result.responseMsg(), result.cdrXml(), result.retryable());
    }

    private String sunatEndpoint(Tenant tenant) {
        if (tenant.getSunatEndpoint() != null && !tenant.getSunatEndpoint().isBlank()) {
            return tenant.getSunatEndpoint();
        }
        return "PRODUCTION".equalsIgnoreCase(tenant.getEnvironment())
            ? SunatBillService.PROD_URL : SunatBillService.BETA_URL;
    }

    private XmlSigner createSigner(Tenant tenant) {
        try {
            String certPath = Path.of(certsBasePath, tenant.getCertificateFile()).toString();
            return new XmlSigner(new FileInputStream(certPath), secretResolver.resolve(tenant.getCertificatePassword()),
                tenant.getCertificateAlias());
        } catch (Exception e) {
            throw new RuntimeException("Cannot load certificate for tenant " + tenant.getBusinessCode(), e);
        }
    }

    private DocumentStorage getStorage() {
        String configured = storageEncryptionKey.orElse(null);
        String resolved = configured == null || configured.isBlank() ? null : secretResolver.resolve(configured);
        return new DocumentStorage(storagePath, resolved);
    }

    // --- Document building (uses tenant defaults) ---

    Document buildDocument(EmitDocumentRequest req, Tenant tenant) {
        Document doc = new Document();
        doc.setBusinessCode(req.getBusinessCode());
        doc.setDocType(DocumentType.fromSunatCode(req.getDocType()));
        doc.setSendMode(tenant.getSendMode());

        // Series: use request if provided, otherwise tenant default
        String series = req.getSeries();
        if (series == null || series.isBlank()) {
            series = resolveDefaultSeries(doc.getDocType(), tenant);
        }
        doc.setSeries(series);
        doc.setNumber(req.getNumber());

        doc.setIssueDate(req.getIssueDate() != null ? req.getIssueDate() : fiscalClock.today());
        doc.setDueDate(req.getDueDate());
        doc.setCurrency(req.getCurrency() != null ? req.getCurrency() : "PEN");
        doc.setCallbackUrl(req.getCallbackUrl());
        doc.setExternalId(req.getExternalId());
        doc.setSourceDocId(req.getSourceDocId());
        doc.setProducerId(parseUuid(req.getProducerId()));
        doc.setProducerCode(req.getProducerCode());

        // Issuer: use request if provided, otherwise tenant defaults
        if (req.getIssuer() != null) {
            doc.setIssuerRuc(req.getIssuer().getRuc());
            doc.setIssuerName(req.getIssuer().getName());
            doc.setIssuerTrade(req.getIssuer().getTradeName());
            doc.setIssuerAddress(req.getIssuer().getAddress());
            doc.setIssuerUbigeo(req.getIssuer().getUbigeo());
        } else {
            doc.setIssuerRuc(tenant.getIssuerRuc());
            doc.setIssuerName(tenant.getIssuerName());
            doc.setIssuerTrade(tenant.getIssuerTrade());
            doc.setIssuerAddress(tenant.getIssuerAddress());
            doc.setIssuerUbigeo(tenant.getIssuerUbigeo());
        }

        // Receiver
        if (req.getReceiver() != null) {
            doc.setReceiverDocType(req.getReceiver().getDocType());
            doc.setReceiverDoc(req.getReceiver().getDoc());
            doc.setReceiverName(req.getReceiver().getName());
            doc.setReceiverAddress(req.getReceiver().getAddress());
        }

        // Lines
        BigDecimal totalTaxBase = BigDecimal.ZERO;
        BigDecimal totalIgv = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Map<String, BigDecimal[]> taxesByAffectation = new LinkedHashMap<>();

        List<EmitDocumentRequest.Line> requestLines = req.getLines() != null ? req.getLines() : List.of();
        for (int i = 0; i < requestLines.size(); i++) {
            var reqLine = requestLines.get(i);
            DocumentLine line = new DocumentLine();
            line.setDocument(doc);
            line.setLineNumber(i + 1);
            line.setProductCode(reqLine.getProductCode());
            line.setProductName(reqLine.getProductName());
            line.setUnitCode(reqLine.getUnitCode() != null ? reqLine.getUnitCode() : "NIU");
            line.setQuantity(reqLine.getQuantity());
            line.setUnitPrice(reqLine.getUnitPrice());
            line.setDiscount(reqLine.getDiscount() != null ? reqLine.getDiscount() : BigDecimal.ZERO);
            String affectationCode = reqLine.getAffectationCode() != null ? reqLine.getAffectationCode() : "10";
            var taxProfile = PeruTaxRules.profile(affectationCode);
            line.setAffectationCode(affectationCode);

            BigDecimal unroundedBase = reqLine.getQuantity().multiply(reqLine.getUnitPrice())
                .subtract(line.getDiscount());
            BigDecimal lineBase = unroundedBase.setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxRate = taxProfile != null ? taxProfile.rate() : BigDecimal.ZERO;
            BigDecimal lineIgv = unroundedBase.multiply(taxRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            line.setTaxBase(lineBase);
            line.setIgvAmount(lineIgv);
            line.setIgvRate(taxRate);
            line.setGrossPrice(reqLine.getUnitPrice()
                .multiply(BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100"))))
                .setScale(6, RoundingMode.HALF_UP));
            line.setLineTotal(lineBase.add(lineIgv));

            doc.getLines().add(line);
            totalTaxBase = totalTaxBase.add(lineBase);
            totalIgv = totalIgv.add(lineIgv);
            totalAmount = totalAmount.add(line.getLineTotal());
            totalDiscount = totalDiscount.add(line.getDiscount());
            if (taxProfile != null) {
                BigDecimal[] aggregate = taxesByAffectation.computeIfAbsent(
                    affectationCode, ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                aggregate[0] = aggregate[0].add(lineBase);
                aggregate[1] = aggregate[1].add(lineIgv);
            }
        }

        doc.setTaxBase(totalTaxBase);
        doc.setIgvAmount(totalIgv);
        doc.setTotal(totalAmount);
        doc.setDiscountTotal(totalDiscount);

        taxesByAffectation.forEach((code, aggregate) -> {
            var profile = PeruTaxRules.profile(code);
            DocumentTax tax = new DocumentTax();
            tax.setDocument(doc);
            tax.setTaxCode(profile.taxCode());
            tax.setTaxName(profile.taxName());
            tax.setTaxBase(aggregate[0]);
            tax.setTaxAmount(aggregate[1]);
            tax.setTaxRate(profile.rate());
            doc.getTaxes().add(tax);
        });

        // Reference (NC/ND)
        if (req.getReference() != null) {
            doc.setRefDocType(req.getReference().getDocType());
            doc.setRefSeries(req.getReference().getSeries());
            doc.setRefNumber(req.getReference().getNumber());
            doc.setRefReasonCode(req.getReference().getReasonCode());
            doc.setRefReason(req.getReference().getReason());
        }

        return doc;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private String resolveDefaultSeries(DocumentType docType, Tenant tenant) {
        return switch (docType) {
            case INVOICE -> tenant.getInvoiceSeries();
            case RECEIPT -> tenant.getReceiptSeries();
            case CREDIT_NOTE -> tenant.getCreditNoteSeries();
            case DEBIT_NOTE -> tenant.getDebitNoteSeries();
            default -> "F001";
        };
    }

    private void logSend(Document doc, SendResult result) {
        DocumentSendLog log = new DocumentSendLog();
        log.setDocument(doc);
        log.setAttempt(doc.getRetryCount() + 1);
        log.setSendMode(doc.getSendMode().name());
        log.setStatus(result.status().name());
        log.setTicket(result.ticket());
        log.setResponseCode(result.responseCode());
        log.setResponseMsg(result.responseMsg());
        if (result.cdrXml() != null) {
            log.setCdrXml(result.cdrXml());
        }
        em.persist(log);
    }

    private void applySendResult(Document doc, SendResult result) {
        doc.setSunatTicket(result.ticket());
        doc.setSunatResponse(result.responseMsg());
        doc.setStatus(result.status());
        doc.setLastAttemptAt(Instant.now());
        if (result.retryable() && result.status() == DocumentStatus.ERROR) {
            scheduleRetry(doc, result.responseMsg());
        } else {
            doc.setNextRetryAt(null);
        }
    }

    private void scheduleRetry(Document doc, String message) {
        doc.setStatus(doc.getRetryCount() >= MAX_RETRIES ? DocumentStatus.FAILED : DocumentStatus.ERROR);
        doc.setSunatResponse(message);
        doc.setLastAttemptAt(Instant.now());
        if (doc.getStatus() == DocumentStatus.FAILED) {
            doc.setNextRetryAt(null);
        } else {
            long minutes = switch (doc.getRetryCount()) { case 0 -> 1; case 1 -> 5; case 2 -> 15; default -> 60; };
            doc.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(minutes)));
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
            ? exception.getMessage() : "La operación no pudo completarse";
    }

    record SendResult(DocumentStatus status, String digestValue, String ticket, String responseCode,
                      String responseMsg, String cdrXml, boolean retryable) {}
}
