package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.app.security.SecretResolver;
import io.carabo.core.dto.EmitDispatchGuideRequest;
import io.carabo.core.entity.Tenant;
import io.carabo.core.entity.gre.DispatchGuide;
import io.carabo.core.entity.gre.DispatchGuideItem;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.ubl.gre.GreUblBuilder;
import io.carabo.signer.XmlSigner;
import io.carabo.storage.DocumentStorage;
import io.carabo.sunat.gre.SunatGreClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DispatchGuideService {

  private static final Logger LOG = Logger.getLogger(DispatchGuideService.class);

  @Inject EntityManager em;

  @Inject TenantService tenantService;

  @Inject CorrelativeService correlativeService;

  @Inject IdempotencyService idempotencyService;

  @Inject SecretResolver secretResolver;

  @Inject FiscalClock fiscalClock;

  @ConfigProperty(name = "carabo.certs.base-path", defaultValue = "/app/certs")
  String certsBasePath;

  @ConfigProperty(name = "carabo.storage.base-path", defaultValue = "/app/storage/documents")
  String storagePath;

  @ConfigProperty(name = "carabo.storage.encryption-key")
  Optional<String> storageEncryptionKey;

  @ConfigProperty(name = "carabo.sunat.gre.client-id")
  Optional<String> greClientId;

  @ConfigProperty(name = "carabo.sunat.gre.client-secret")
  Optional<String> greClientSecret;

  @Inject ObjectMapper objectMapper;
  private static final int MAX_RETRIES = 8;

  private final GreUblBuilder ublBuilder = new GreUblBuilder();

  @Transactional
  public DispatchGuide emit(EmitDispatchGuideRequest request) {
    Tenant tenant = tenantService.resolve(request.getBusinessCode());

    request.setExternalId(IdempotencyService.normalize(request.getExternalId()));
    idempotencyService.lock("DISPATCH_GUIDE", request.getBusinessCode(), request.getExternalId());
    DispatchGuide existing = findByExternalId(request.getBusinessCode(), request.getExternalId());
    String payloadHash = payloadHash(request);
    if (existing != null) {
      if (existing.getPayloadHash() != null && !existing.getPayloadHash().equals(payloadHash))
        throw new jakarta.ws.rs.ClientErrorException(
            "La solicitud de guía ya existe con otros datos", 409);
      return existing;
    }

    DispatchGuide gre = new DispatchGuide();
    gre.setBusinessCode(request.getBusinessCode());
    gre.setSeries(request.getSeries() != null ? request.getSeries() : "T001");
    gre.setPayloadHash(payloadHash);
    gre.setEnvironment(tenant.getEnvironment());
    gre.setIssueDate(request.getIssueDate() != null ? request.getIssueDate() : fiscalClock.today());
    gre.setStartDate(request.getStartDate() != null ? request.getStartDate() : fiscalClock.today());

    // Issuer from tenant
    gre.setIssuerRuc(tenant.getIssuerRuc());
    gre.setIssuerName(tenant.getIssuerName());

    // Receiver
    if (request.getReceiver() != null) {
      gre.setReceiverDocType(request.getReceiver().getDocType());
      gre.setReceiverDoc(request.getReceiver().getDoc());
      gre.setReceiverName(request.getReceiver().getName());
    }

    // Transport
    gre.setTransportMode(request.getTransportMode());
    gre.setGrossWeight(request.getGrossWeight());
    gre.setWeightUnit(request.getWeightUnit() != null ? request.getWeightUnit() : "KGM");
    gre.setPackageCount(request.getPackageCount());

    // Addresses
    if (request.getOrigin() != null) {
      gre.setOriginUbigeo(request.getOrigin().getUbigeo());
      gre.setOriginAddress(request.getOrigin().getAddress());
    }
    if (request.getDestination() != null) {
      gre.setDestinationUbigeo(request.getDestination().getUbigeo());
      gre.setDestinationAddress(request.getDestination().getAddress());
    }

    // Carrier (public transport)
    if (request.getCarrier() != null) {
      gre.setCarrierRuc(request.getCarrier().getRuc());
      gre.setCarrierName(request.getCarrier().getName());
    }

    // Driver + vehicle (private transport)
    if (request.getDriver() != null) {
      gre.setDriverDocType(request.getDriver().getDocType());
      gre.setDriverDoc(request.getDriver().getDoc());
      gre.setDriverName(request.getDriver().getName());
      gre.setDriverLicense(request.getDriver().getLicense());
    }
    gre.setVehiclePlate(request.getVehiclePlate());

    // Transfer reason
    gre.setTransferReasonCode(request.getTransferReasonCode());
    gre.setTransferReasonDesc(request.getTransferReasonDesc());

    // Related document
    if (request.getRelatedDoc() != null) {
      gre.setRelatedDocType(request.getRelatedDoc().getDocType());
      gre.setRelatedDocNumber(request.getRelatedDoc().getNumber());
    }

    gre.setCallbackUrl(request.getCallbackUrl());
    gre.setExternalId(request.getExternalId());

    // Items
    if (request.getItems() != null) {
      for (int i = 0; i < request.getItems().size(); i++) {
        var reqItem = request.getItems().get(i);
        DispatchGuideItem item = new DispatchGuideItem();
        item.setDispatchGuide(gre);
        item.setLineNumber(i + 1);
        item.setProductCode(reqItem.getProductCode());
        item.setProductName(reqItem.getProductName());
        item.setUnitCode(reqItem.getUnitCode() != null ? reqItem.getUnitCode() : "NIU");
        item.setQuantity(reqItem.getQuantity());
        item.setWeight(reqItem.getWeight());
        gre.getItems().add(item);
      }
    }

    boolean configured =
        greCredentialsConfigured(tenant) && greEnvironmentBlocker(tenant.getEnvironment()) == null;
    gre.setStatus(configured ? DocumentStatus.PENDING : DocumentStatus.BLOCKED_CONFIGURATION);
    gre.setSunatResponse(
        configured
            ? "Solicitud guardada; pendiente de envío a SUNAT. No habilita el traslado"
            : "Solicitud guardada. Configura las credenciales GRE del emisor en Carabo; no habilita"
                + " el traslado");
    if (greEnvironmentBlocker(tenant.getEnvironment()) != null)
      gre.setSunatResponse(greEnvironmentBlocker(tenant.getEnvironment()));
    gre.setNextRetryAt(
        greEnvironmentBlocker(tenant.getEnvironment()) == null ? Instant.now() : null);
    em.persist(gre);
    em.flush();
    return gre;
  }

  private String payloadHash(EmitDispatchGuideRequest request) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(request)));
    } catch (Exception error) {
      throw new IllegalArgumentException("No se pudo identificar la solicitud GRE", error);
    }
  }

  public java.util.List<DispatchGuide> findSubmissionsDue(int limit) {
    return em.createQuery(
            "SELECT g FROM DispatchGuide g WHERE g.sunatTicket IS NULL AND g.status IN (:statuses)"
                + " AND (g.nextRetryAt <= :now OR (g.environment <> 'PRODUCTION' AND g.nextRetryAt"
                + " IS NOT NULL)) ORDER BY g.createdAt",
            DispatchGuide.class)
        .setParameter(
            "statuses",
            java.util.List.of(
                DocumentStatus.PENDING,
                DocumentStatus.VALIDATED,
                DocumentStatus.BLOCKED_CONFIGURATION,
                DocumentStatus.RETRY_PENDING))
        .setParameter("now", Instant.now())
        .setMaxResults(Math.min(Math.max(limit, 1), 50))
        .getResultList();
  }

  /** The claim commits before HTTP; an interrupted submission is never blindly sent again. */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public Submission claimSubmission(UUID id, String businessCode) {
    DispatchGuide guide = locked(id, businessCode);
    if (guide == null
        || guide.getSunatTicket() != null
        || !java.util.Set.of(
                DocumentStatus.PENDING,
                DocumentStatus.VALIDATED,
                DocumentStatus.BLOCKED_CONFIGURATION,
                DocumentStatus.RETRY_PENDING)
            .contains(guide.getStatus())) return null;
    Tenant tenant = tenantService.resolve(businessCode);
    if (!java.util.Objects.equals(guide.getEnvironment(), tenant.getEnvironment())
        || !java.util.Objects.equals(guide.getIssuerRuc(), tenant.getIssuerRuc())) {
      guide.setStatus(DocumentStatus.BLOCKED_CONFIGURATION);
      guide.setSunatResponse(
          "El emisor o ambiente fiscal cambió después de guardar la solicitud. Requiere revisión"
              + " antes del envío");
      guide.setNextRetryAt(null);
      return null;
    }
    String environmentBlocker = greEnvironmentBlocker(guide.getEnvironment());
    if (environmentBlocker != null) {
      guide.setStatus(DocumentStatus.BLOCKED_CONFIGURATION);
      guide.setSunatResponse(environmentBlocker);
      guide.setNextRetryAt(null);
      return null;
    }
    if (guide.getNextRetryAt() != null && guide.getNextRetryAt().isAfter(Instant.now()))
      return null;
    if (!greCredentialsConfigured(tenant)) {
      guide.setStatus(DocumentStatus.BLOCKED_CONFIGURATION);
      guide.setSunatResponse(
          "Configura las credenciales GRE del emisor en Carabo. No habilita el traslado");
      guide.setNextRetryAt(Instant.now().plusSeconds(300));
      return null;
    }
    if (guide.getStartDate().isBefore(fiscalClock.today())) {
      guide.setStatus(DocumentStatus.ERROR);
      guide.setSunatResponse(
          "La fecha prevista de traslado venció sin aceptación. Revisa la solicitud antes de emitir"
              + " otra guía");
      guide.setNextRetryAt(null);
      return null;
    }
    try {
      if (!credential(tenant.getSolUser())
          || !credential(secretResolver.resolve(tenant.getSolPassword()))) {
        guide.setStatus(DocumentStatus.BLOCKED_CONFIGURATION);
        guide.setSunatResponse(
            "Completa las credenciales SOL del emisor para GRE. No habilita el traslado");
        guide.setNextRetryAt(Instant.now().plusSeconds(300));
        return null;
      }
      if (guide.getNumber() == null)
        guide.setNumber(
            correlativeService.next(
                businessCode, CorrelativeService.Kind.DISPATCH_GUIDE, guide.getSeries()));
      String xml =
          getStorage()
              .getSignedXml(businessCode, guide.getSeries(), guide.getNumber())
              .orElse(null);
      if (xml == null) {
        var signed = signGuide(guide, tenant);
        guide.setHashCode(signed.digestValue());
        xml = signed.signedXml();
        getStorage().storeSignedXml(businessCode, guide.getSeries(), guide.getNumber(), xml);
      }
      guide.setStatus(DocumentStatus.SIGNED);
      guide.setSunatResponse(
          "Envío iniciado. Si no se confirma un ticket, requiere conciliación antes de reenviar. No"
              + " habilita el traslado");
      guide.setNextRetryAt(null);
      return new Submission(
          guide.getId(),
          businessCode,
          guide.getIssuerRuc() + "-09-" + guide.getFullNumber(),
          xml,
          tenant);
    } catch (Exception error) {
      LOG.warnf(
          "GRE %s blocked before submission: %s", guide.getId(), error.getClass().getSimpleName());
      guide.setStatus(DocumentStatus.BLOCKED_CONFIGURATION);
      guide.setSunatResponse(
          "No se pudo preparar la firma GRE. Revisa el certificado y los secretos del emisor; no"
              + " habilita el traslado");
      guide.setNextRetryAt(Instant.now().plusSeconds(300));
      return null;
    }
  }

  public SunatGreClient.GreSendResult submit(Submission submission) {
    return greClient(submission.tenant()).send(submission.filename(), submission.xml());
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void recordSubmission(Submission submission, SunatGreClient.GreSendResult result) {
    DispatchGuide guide = locked(submission.id(), submission.businessCode());
    if (guide == null
        || guide.getStatus() != DocumentStatus.SIGNED
        || guide.getSunatTicket() != null) return;
    if (result.success() && result.ticket() != null && !result.ticket().isBlank()) {
      guide.setSunatTicket(result.ticket());
      guide.setStatus(DocumentStatus.SENT);
      guide.setRetryCount(0);
      guide.setSunatResponse("Enviada a SUNAT; pendiente de CDR. No habilita el traslado");
      guide.setNextRetryAt(Instant.now().plusSeconds(15));
    } else {
      int attempts = guide.getRetryCount() + 1;
      guide.setRetryCount(attempts);
      boolean retry = result.retryable() && !result.uncertain() && attempts < MAX_RETRIES;
      guide.setStatus(retry ? DocumentStatus.RETRY_PENDING : DocumentStatus.ERROR);
      guide.setSunatResponse(result.error());
      guide.setSunatResponseCode(
          result.uncertain()
              ? "GRE_SUBMISSION_UNCERTAIN"
              : result.retryable() ? "GRE_PRE_SUBMISSION_RETRYABLE" : "GRE_REQUEST_ERROR");
      guide.setNextRetryAt(retry ? retryAt(attempts) : null);
    }
  }

  protected XmlSigner.SignResult signGuide(DispatchGuide guide, Tenant tenant) {
    return createSigner(tenant).sign(ublBuilder.build(guide));
  }

  private DispatchGuide locked(UUID id, String businessCode) {
    return em.createQuery(
            "SELECT g FROM DispatchGuide g WHERE g.id=:id AND g.businessCode=:bc",
            DispatchGuide.class)
        .setParameter("id", id)
        .setParameter("bc", businessCode)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }

  public static String greEnvironmentBlocker(String environment) {
    return "PRODUCTION".equals(environment)
        ? null
        : "El ambiente "
            + environment
            + " no tiene un canal de recepción GRE 2.0 de pruebas en SUNAT. La solicitud se"
            + " conserva sin envío. Puedes validar el XML con SFS; no habilita el traslado";
  }

  public String retryBlocker(DispatchGuide guide) {
    String environment = greEnvironmentBlocker(guide.getEnvironment());
    if (environment != null) return environment;
    if (java.util.Set.of(
            DocumentStatus.ACCEPTED,
            DocumentStatus.OBSERVED,
            DocumentStatus.REJECTED,
            DocumentStatus.VOIDED,
            DocumentStatus.CANCELLED_LOCAL)
        .contains(guide.getStatus()))
      return "La guía tiene un resultado fiscal definitivo; no se reenvía";
    if (guide.getStatus() == DocumentStatus.SIGNED
        || guide.getStatus() == DocumentStatus.ERROR
            && guide.getSunatTicket() == null
            && !"GRE_PRE_SUBMISSION_RETRYABLE".equals(guide.getSunatResponseCode()))
      return "Requiere revisión y conciliación antes de volver a enviar. No se puede descartar una"
          + " recepción previa";
    if (guide.getSunatTicket() == null && guide.getStartDate().isBefore(fiscalClock.today()))
      return "La fecha prevista de traslado venció; revisa el documento antes de continuar";
    return null;
  }

  /** Queue the existing document only. Ticketed documents are queried, never resubmitted. */
  @Transactional
  public Optional<DispatchGuide> requestRetry(UUID id, String businessCode, String actor) {
    DispatchGuide guide = locked(id, businessCode);
    if (guide == null) return Optional.empty();
    String blocker = retryBlocker(guide);
    if (blocker != null) throw new jakarta.ws.rs.ClientErrorException(blocker, 409);
    if (guide.getNextRetryAt() != null && !guide.getNextRetryAt().isAfter(Instant.now()))
      return Optional.of(guide);
    if (guide.getRetryCount() >= MAX_RETRIES) guide.setRetryCount(0);
    guide.setNextRetryAt(Instant.now());
    if (guide.getStatus() == DocumentStatus.ERROR) guide.setStatus(DocumentStatus.RETRY_PENDING);
    LOG.infof(
        "GRE retry requested id=%s business=%s actor=%s action=%s",
        id, businessCode, actor, guide.getSunatTicket() == null ? "SEND" : "CHECK_STATUS");
    return Optional.of(guide);
  }

  private Instant retryAt(int attempts) {
    return Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(attempts, 7)));
  }

  public record Submission(
      UUID id, String businessCode, String filename, String xml, Tenant tenant) {}

  public Optional<DispatchGuide> findById(UUID id) {
    return Optional.ofNullable(em.find(DispatchGuide.class, id));
  }

  public Optional<DispatchGuide> findById(UUID id, String businessCode) {
    return em.createQuery(
            "SELECT g FROM DispatchGuide g WHERE g.id = :id AND g.businessCode = :bc",
            DispatchGuide.class)
        .setParameter("id", id)
        .setParameter("bc", businessCode)
        .getResultStream()
        .findFirst();
  }

  public java.util.List<DispatchGuide> findStates(java.util.List<UUID> ids, String businessCode) {
    if (ids == null || ids.isEmpty() || ids.size() > 100)
      throw new jakarta.ws.rs.BadRequestException("Consulta entre 1 y 100 guías");
    return em.createQuery(
            "SELECT g FROM DispatchGuide g WHERE g.businessCode=:bc AND g.id IN (:ids)",
            DispatchGuide.class)
        .setParameter("bc", businessCode)
        .setParameter("ids", ids)
        .setMaxResults(100)
        .getResultList();
  }

  @SuppressWarnings("unchecked")
  public java.util.List<DispatchGuide> findByBusiness(String businessCode, int offset, int limit) {
    return em.createQuery(
            "SELECT g FROM DispatchGuide g WHERE g.businessCode = :bc ORDER BY g.createdAt DESC")
        .setParameter("bc", businessCode)
        .setFirstResult(offset)
        .setMaxResults(limit)
        .getResultList();
  }

  public java.util.List<DispatchGuide> findTicketsDue(int limit) {
    return em.createQuery(
            "SELECT g FROM DispatchGuide g WHERE g.sunatTicket IS NOT NULL AND g.retryCount < :max"
                + " AND (g.status = :sent AND g.nextRetryAt IS NULL OR g.status IN (:statuses) AND"
                + " g.nextRetryAt <= :now) ORDER BY g.updatedAt",
            DispatchGuide.class)
        .setParameter("sent", DocumentStatus.SENT)
        .setParameter(
            "statuses",
            java.util.List.of(
                DocumentStatus.SENT,
                DocumentStatus.PENDING,
                DocumentStatus.RETRY_PENDING,
                DocumentStatus.ERROR))
        .setParameter("max", MAX_RETRIES)
        .setParameter("now", Instant.now())
        .setMaxResults(Math.min(Math.max(limit, 1), 100))
        .getResultList();
  }

  @Transactional
  public Optional<DispatchGuide> checkStatus(UUID id, String businessCode) {
    DispatchGuide guide = locked(id, businessCode);
    if (guide == null) return Optional.empty();
    if (guide.getSunatTicket() == null
        || java.util.Set.of(
                DocumentStatus.ACCEPTED,
                DocumentStatus.OBSERVED,
                DocumentStatus.REJECTED,
                DocumentStatus.VOIDED,
                DocumentStatus.CANCELLED_LOCAL)
            .contains(guide.getStatus())) return Optional.of(guide);
    var result = greClient(tenantService.resolve(businessCode)).checkStatus(guide.getSunatTicket());
    guide.setSunatResponseCode(result.code());
    guide.setSunatResponse(result.message());
    if ((result.accepted() || result.rejected()) && result.cdrXml() != null) {
      getStorage().storeCdr(businessCode, guide.getSeries(), guide.getNumber(), result.cdrXml());
      guide.setStatus(
          result.accepted()
              ? (result.observed() ? DocumentStatus.OBSERVED : DocumentStatus.ACCEPTED)
              : DocumentStatus.REJECTED);
      guide.setNextRetryAt(null);
      guide.setRetryCount(0);
    } else if ("98".equals(result.code())) {
      guide.setStatus(DocumentStatus.PENDING);
      guide.setRetryCount(0);
      guide.setNextRetryAt(Instant.now().plusSeconds(30));
    } else {
      int attempts = guide.getRetryCount() + 1;
      guide.setRetryCount(attempts);
      guide.setStatus(attempts < MAX_RETRIES ? DocumentStatus.RETRY_PENDING : DocumentStatus.ERROR);
      guide.setNextRetryAt(attempts < MAX_RETRIES ? retryAt(attempts) : null);
    }
    return Optional.of(guide);
  }

  public Optional<String> getSignedXml(UUID id, String businessCode) {
    return findById(id, businessCode)
        .filter(guide -> guide.getNumber() != null)
        .flatMap(
            guide ->
                getStorage()
                    .getSignedXml(guide.getBusinessCode(), guide.getSeries(), guide.getNumber()));
  }

  public Optional<String> getCdr(UUID id, String businessCode) {
    return findById(id, businessCode)
        .filter(guide -> guide.getNumber() != null)
        .flatMap(
            guide ->
                getStorage().getCdr(guide.getBusinessCode(), guide.getSeries(), guide.getNumber()));
  }

  protected SunatGreClient greClient(Tenant tenant) {
    boolean isBeta = "BETA".equals(tenant.getEnvironment());
    String solUser = greSolUser(tenant.getIssuerRuc(), tenant.getSolUser());
    return new SunatGreClient(
        effectiveClientId(tenant),
        secretResolver.resolve(effectiveClientSecret(tenant)),
        solUser,
        secretResolver.resolve(tenant.getSolPassword()),
        isBeta);
  }

  public boolean greCredentialsConfigured(String businessCode) {
    return greCredentialsConfigured(tenantService.resolve(businessCode));
  }

  boolean greCredentialsConfigured(Tenant tenant) {
    try {
      return credential(effectiveClientId(tenant))
          && credential(secretResolver.resolve(effectiveClientSecret(tenant)));
    } catch (IllegalArgumentException | IllegalStateException missing) {
      return false;
    }
  }

  private boolean credential(String value) {
    return value != null && !value.isBlank() && !"not-configured".equals(value);
  }

  private boolean tenantCredentials(Tenant tenant) {
    return tenant.getGreClientId() != null || tenant.getGreClientSecret() != null;
  }

  private String effectiveClientId(Tenant tenant) {
    return tenantCredentials(tenant) ? tenant.getGreClientId() : greClientId.orElse(null);
  }

  private String effectiveClientSecret(Tenant tenant) {
    return tenantCredentials(tenant) ? tenant.getGreClientSecret() : greClientSecret.orElse(null);
  }

  public boolean printable(DispatchGuide guide) {
    return java.util.Set.of(DocumentStatus.ACCEPTED, DocumentStatus.OBSERVED)
            .contains(guide.getStatus())
        && "PRODUCTION".equals(guide.getEnvironment())
        && getCdr(guide.getId(), guide.getBusinessCode()).isPresent();
  }

  static String greSolUser(String ruc, String user) {
    return user.startsWith(ruc) ? user : ruc + user;
  }

  protected DocumentStorage getStorage() {
    String configured = storageEncryptionKey.orElse(null);
    String resolved =
        configured == null || configured.isBlank() ? null : secretResolver.resolve(configured);
    return new DocumentStorage(storagePath, resolved);
  }

  private XmlSigner createSigner(Tenant tenant) {
    try {
      String certPath = Path.of(certsBasePath, tenant.getCertificateFile()).toString();
      return new XmlSigner(
          new FileInputStream(certPath),
          secretResolver.resolve(tenant.getCertificatePassword()),
          tenant.getCertificateAlias());
    } catch (Exception e) {
      throw new RuntimeException(
          "Cannot load certificate for tenant " + tenant.getBusinessCode(), e);
    }
  }

  private DispatchGuide findByExternalId(String businessCode, String externalId) {
    if (externalId == null) return null;
    return em.createQuery(
            "SELECT g FROM DispatchGuide g WHERE g.businessCode = :bc AND g.externalId = :eid",
            DispatchGuide.class)
        .setParameter("bc", businessCode)
        .setParameter("eid", externalId)
        .getResultStream()
        .findFirst()
        .orElse(null);
  }
}
