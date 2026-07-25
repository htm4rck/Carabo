package io.carabo.app.service;

import io.carabo.core.dto.DocumentCallback;
import io.carabo.core.dto.EmitDocumentRequest;
import io.carabo.core.dto.EmitDocumentResponse;
import io.carabo.core.entity.*;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.enums.SendMode;
import io.carabo.core.ubl.UblBuilder;
import io.carabo.core.validation.DocumentValidator;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class DocumentService {

    private static final Logger LOG = Logger.getLogger(DocumentService.class);
    private static final BigDecimal IGV_RATE = new BigDecimal("0.18");
    private static final int MAX_RETRIES = 3;

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TenantService tenantService;

    @ConfigProperty(name = "carabo.certs.base-path", defaultValue = "/app/certs")
    String certsBasePath;

    @ConfigProperty(name = "carabo.storage.base-path", defaultValue = "/app/storage/documents")
    String storagePath;

    private final UblBuilder ublBuilder = new UblBuilder();
    private final DocumentValidator validator = new DocumentValidator();

    @Transactional
    public EmitDocumentResponse emit(EmitDocumentRequest request) {
        Tenant tenant = tenantService.resolve(request.getBusinessCode());

        // Idempotency by sourceDocId
        if (request.getExternalId() != null) {
            Document existing = findBySourceDoc(request.getBusinessCode(), request.getExternalId());
            if (existing != null) {
                return EmitDocumentResponse.of(
                    existing.getId(), existing.getFullNumber(), existing.getStatus().name(),
                    existing.getHashCode(), existing.getSunatResponse(), existing.getExternalId());
            }
        }

        Document doc = buildDocument(request, tenant);

        // Validate before persisting
        var errors = validator.validate(doc, tenant);
        if (!errors.isEmpty()) {
            doc.setStatus(DocumentStatus.REJECTED);
            doc.setSunatResponse(String.join("; ", errors));
            em.persist(doc);
            return EmitDocumentResponse.of(
                doc.getId(), doc.getFullNumber(), "VALIDATION_ERROR",
                null, doc.getSunatResponse(), doc.getExternalId());
        }

        doc.setStatus(DocumentStatus.VALIDATED);
        em.persist(doc);
        em.flush();

        try {
            String xml = ublBuilder.build(doc);
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signResult = signer.sign(xml);

            doc.setHashCode(signResult.digestValue());
            getStorage().storeSignedXml(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), signResult.signedXml());

            SendResult result = sendToAuthority(doc, signResult.signedXml(), tenant);

            doc.setSunatTicket(result.ticket());
            doc.setSunatResponse(result.responseMsg());
            doc.setStatus(result.accepted() ? DocumentStatus.ACCEPTED : DocumentStatus.REJECTED);

            if (result.cdrXml() != null) {
                getStorage().storeCdr(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), result.cdrXml());
            }

            logSend(doc, result);
        } catch (Exception e) {
            LOG.error("Emit pipeline failed for " + doc.getFullNumber(), e);
            doc.setStatus(DocumentStatus.ERROR);
            doc.setSunatResponse(e.getMessage());
            logSend(doc, new SendResult(false, null, null, null, e.getMessage(), null));
        }

        em.merge(doc);

        if (doc.getCallbackUrl() != null) {
            notifyCallback(doc);
        }
        // Also notify tenant-level webhook if configured
        Tenant t = tenantService.resolve(doc.getBusinessCode());
        if (t.getWebhookUrl() != null && !t.getWebhookUrl().isBlank()) {
            notifyWebhook(doc, t);
        }

        return EmitDocumentResponse.of(
            doc.getId(), doc.getFullNumber(), doc.getStatus().name(),
            doc.getHashCode(), doc.getSunatResponse(), doc.getExternalId()
        );
    }

    @Transactional
    public void retry(UUID documentId) {
        Document doc = em.find(Document.class, documentId);
        if (doc == null || doc.getRetryCount() >= MAX_RETRIES) return;
        if (doc.getStatus() != DocumentStatus.PENDING && doc.getStatus() != DocumentStatus.ERROR) return;

        Tenant tenant = tenantService.resolve(doc.getBusinessCode());
        doc.setRetryCount(doc.getRetryCount() + 1);

        try {
            String xml = ublBuilder.build(doc);
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signResult = signer.sign(xml);

            doc.setHashCode(signResult.digestValue());
            getStorage().storeSignedXml(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), signResult.signedXml());

            SendResult result = sendToAuthority(doc, signResult.signedXml(), tenant);

            doc.setSunatTicket(result.ticket());
            doc.setSunatResponse(result.responseMsg());
            doc.setStatus(result.accepted() ? DocumentStatus.ACCEPTED : DocumentStatus.REJECTED);

            if (result.cdrXml() != null) {
                getStorage().storeCdr(doc.getBusinessCode(), doc.getSeries(), doc.getNumber(), result.cdrXml());
            }

            logSend(doc, result);
        } catch (Exception e) {
            doc.setStatus(DocumentStatus.ERROR);
            doc.setSunatResponse(e.getMessage());
            logSend(doc, new SendResult(false, null, null, null, e.getMessage(), null));
        }

        em.merge(doc);

        if (doc.getCallbackUrl() != null && doc.getStatus() != DocumentStatus.ERROR) {
            notifyCallback(doc);
        }
    }

    public Optional<Document> findById(UUID id) {
        return Optional.ofNullable(em.find(Document.class, id));
    }

    public Optional<String> getSignedXml(UUID id) {
        return findById(id).flatMap(doc ->
            getStorage().getSignedXml(doc.getBusinessCode(), doc.getSeries(), doc.getNumber()));
    }

    public Optional<String> getCdr(UUID id) {
        return findById(id).flatMap(doc ->
            getStorage().getCdr(doc.getBusinessCode(), doc.getSeries(), doc.getNumber()));
    }

    @SuppressWarnings("unchecked")
    public List<Document> findRetryable() {
        return em.createQuery(
            "SELECT d FROM Document d WHERE d.status IN (:statuses) AND d.retryCount < :max")
            .setParameter("statuses", List.of(DocumentStatus.PENDING, DocumentStatus.ERROR, DocumentStatus.VALIDATED))
            .setParameter("max", MAX_RETRIES)
            .getResultList();
    }

    private Document findBySourceDoc(String businessCode, String externalId) {
        try {
            return (Document) em.createQuery(
                "SELECT d FROM Document d WHERE d.businessCode = :bc AND d.externalId = :eid")
                .setParameter("bc", businessCode)
                .setParameter("eid", externalId)
                .setMaxResults(1)
                .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    // --- Pipeline steps (multitenant) ---

    private SendResult sendToAuthority(Document doc, String signedXml, Tenant tenant) {
        String fileName = doc.getIssuerRuc() + "-" + doc.getDocType().sunatCode() + "-" + doc.getFullNumber();

        if (tenant.getSendMode() == SendMode.OSE) {
            return sendViaOse(fileName, signedXml, tenant);
        }
        return sendViaSunat(fileName, signedXml, tenant);
    }

    private SendResult sendViaSunat(String fileName, String signedXml, Tenant tenant) {
        SunatBillService sunat = new SunatBillService(
            tenant.getSunatEndpoint(), tenant.getSolUser(), tenant.getSolPassword());
        SunatBillService.SendBillResult result = sunat.sendBill(fileName, signedXml);
        return new SendResult(result.accepted(), null, result.ticket(),
            result.responseCode(), result.responseMsg(), result.cdrXml());
    }

    private SendResult sendViaOse(String fileName, String signedXml, Tenant tenant) {
        OseClient ose = new OseClient(tenant.getOseBaseUrl(), tenant.getOseApiToken());
        OseClient.OseResult result = ose.send(fileName, signedXml);
        return new SendResult(result.accepted(), result.digestValue(), null,
            result.responseCode(), result.responseMsg(), result.cdrXml());
    }

    private XmlSigner createSigner(Tenant tenant) {
        try {
            String certPath = Path.of(certsBasePath, tenant.getCertificateFile()).toString();
            return new XmlSigner(new FileInputStream(certPath), tenant.getCertificatePassword());
        } catch (Exception e) {
            throw new RuntimeException("Cannot load certificate for tenant " + tenant.getBusinessCode(), e);
        }
    }

    private DocumentStorage getStorage() {
        return new DocumentStorage(storagePath);
    }

    // --- Document building (uses tenant defaults) ---

    private Document buildDocument(EmitDocumentRequest req, Tenant tenant) {
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
        doc.setNumber(nextNumber(req.getBusinessCode(), series));

        doc.setIssueDate(req.getIssueDate() != null ? req.getIssueDate() : LocalDate.now());
        doc.setDueDate(req.getDueDate());
        doc.setCurrency(req.getCurrency() != null ? req.getCurrency() : "PEN");
        doc.setCallbackUrl(req.getCallbackUrl());
        doc.setExternalId(req.getExternalId());
        doc.setSourceDocId(req.getSourceDocId());

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

        for (int i = 0; i < req.getLines().size(); i++) {
            var reqLine = req.getLines().get(i);
            DocumentLine line = new DocumentLine();
            line.setDocument(doc);
            line.setLineNumber(i + 1);
            line.setProductCode(reqLine.getProductCode());
            line.setProductName(reqLine.getProductName());
            line.setUnitCode(reqLine.getUnitCode() != null ? reqLine.getUnitCode() : "NIU");
            line.setQuantity(reqLine.getQuantity());
            line.setUnitPrice(reqLine.getUnitPrice());
            line.setDiscount(reqLine.getDiscount() != null ? reqLine.getDiscount() : BigDecimal.ZERO);
            line.setAffectationCode(reqLine.getAffectationCode() != null ? reqLine.getAffectationCode() : "10");

            BigDecimal lineBase = reqLine.getQuantity().multiply(reqLine.getUnitPrice())
                .subtract(line.getDiscount()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineIgv = lineBase.multiply(IGV_RATE).setScale(2, RoundingMode.HALF_UP);

            line.setTaxBase(lineBase);
            line.setIgvAmount(lineIgv);
            line.setGrossPrice(reqLine.getUnitPrice().multiply(BigDecimal.ONE.add(IGV_RATE)).setScale(6, RoundingMode.HALF_UP));
            line.setLineTotal(lineBase.add(lineIgv));

            doc.getLines().add(line);
            totalTaxBase = totalTaxBase.add(lineBase);
            totalIgv = totalIgv.add(lineIgv);
            totalAmount = totalAmount.add(line.getLineTotal());
        }

        doc.setTaxBase(totalTaxBase);
        doc.setIgvAmount(totalIgv);
        doc.setTotal(totalAmount);

        DocumentTax igvTax = new DocumentTax();
        igvTax.setDocument(doc);
        igvTax.setTaxCode("1000");
        igvTax.setTaxName("IGV");
        igvTax.setTaxBase(totalTaxBase);
        igvTax.setTaxAmount(totalIgv);
        igvTax.setTaxRate(new BigDecimal("18.00"));
        doc.getTaxes().add(igvTax);

        // Reference (NC/ND)
        if (req.getReference() != null) {
            doc.setRefDocType(req.getReference().getDocType());
            doc.setRefSeries(req.getReference().getSeries());
            doc.setRefNumber(req.getReference().getNumber());
            doc.setRefReason(req.getReference().getReason());
        }

        return doc;
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

    private int nextNumber(String businessCode, String series) {
        Object result = em.createQuery(
            "SELECT COALESCE(MAX(d.number), 0) FROM Document d WHERE d.businessCode = :bc AND d.series = :s")
            .setParameter("bc", businessCode)
            .setParameter("s", series)
            .getSingleResult();
        return ((Number) result).intValue() + 1;
    }

    private void logSend(Document doc, SendResult result) {
        DocumentSendLog log = new DocumentSendLog();
        log.setDocument(doc);
        log.setAttempt(doc.getRetryCount() + 1);
        log.setSendMode(doc.getSendMode().name());
        log.setStatus(result.accepted() ? "ACCEPTED" : "ERROR");
        log.setTicket(result.ticket());
        log.setResponseCode(result.responseCode());
        log.setResponseMsg(result.responseMsg());
        if (result.cdrXml() != null) {
            log.setCdrXml(result.cdrXml());
        }
        em.persist(log);
    }

    private void notifyCallback(Document doc) {
        try {
            DocumentCallback cb = doc.getStatus() == DocumentStatus.ACCEPTED
                ? DocumentCallback.success(doc.getId(), doc.getExternalId(), doc.getFullNumber(), doc.getHashCode())
                : DocumentCallback.failure(doc.getId(), doc.getExternalId(), doc.getStatus().name(), null, doc.getSunatResponse());

            String json = objectMapper.writeValueAsString(cb);

            HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder()
                    .uri(URI.create(doc.getCallbackUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            LOG.warn("Callback notification failed for " + doc.getFullNumber(), e);
        }
    }

    private void notifyWebhook(Document doc, Tenant tenant) {
        try {
            DocumentCallback cb = doc.getStatus() == DocumentStatus.ACCEPTED
                ? DocumentCallback.success(doc.getId(), doc.getExternalId(), doc.getFullNumber(), doc.getHashCode())
                : DocumentCallback.failure(doc.getId(), doc.getExternalId(), doc.getStatus().name(), null, doc.getSunatResponse());

            String json = objectMapper.writeValueAsString(cb);

            var reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(tenant.getWebhookUrl()))
                .header("Content-Type", "application/json");

            // Sign webhook with HMAC-SHA256 if secret is configured
            if (tenant.getWebhookSecret() != null && !tenant.getWebhookSecret().isBlank()) {
                String signature = hmacSha256(json, tenant.getWebhookSecret());
                reqBuilder.header("X-Carabo-Signature", signature);
            }

            HttpClient.newHttpClient().sendAsync(
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            LOG.warn("Webhook notification failed for " + doc.getFullNumber(), e);
        }
    }

    private String hmacSha256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "";
        }
    }

    record SendResult(boolean accepted, String digestValue, String ticket, String responseCode, String responseMsg, String cdrXml) {}
}
