package io.carabo.app.service;

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
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DispatchGuideService {

    private static final Logger LOG = Logger.getLogger(DispatchGuideService.class);

    @Inject
    EntityManager em;

    @Inject
    TenantService tenantService;

    @Inject
    CorrelativeService correlativeService;

    @Inject
    IdempotencyService idempotencyService;

    @Inject
    SecretResolver secretResolver;

    @Inject
    FiscalClock fiscalClock;

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

    private final GreUblBuilder ublBuilder = new GreUblBuilder();

    @Transactional
    public DispatchGuide emit(EmitDispatchGuideRequest request) {
        Tenant tenant = tenantService.resolve(request.getBusinessCode());

        request.setExternalId(IdempotencyService.normalize(request.getExternalId()));
        idempotencyService.lock("DISPATCH_GUIDE", request.getBusinessCode(), request.getExternalId());
        DispatchGuide existing = findByExternalId(request.getBusinessCode(), request.getExternalId());
        if (existing != null) return existing;
        if (!greCredentialsConfigured()) throw new jakarta.ws.rs.BadRequestException("Configura las credenciales OAuth de GRE antes de emitir una guía");

        DispatchGuide gre = new DispatchGuide();
        gre.setBusinessCode(request.getBusinessCode());
        gre.setSeries(request.getSeries() != null ? request.getSeries() : "T001");
        gre.setNumber(correlativeService.next(
            request.getBusinessCode(), CorrelativeService.Kind.DISPATCH_GUIDE, gre.getSeries()));
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

        gre.setStatus(DocumentStatus.VALIDATED);
        em.persist(gre);
        em.flush();

        // Build UBL XML, sign, and send to SUNAT REST
        try {
            String xml = ublBuilder.build(gre);
            XmlSigner signer = createSigner(tenant);
            XmlSigner.SignResult signResult = signer.sign(xml);
            gre.setHashCode(signResult.digestValue());

            // Store signed XML
            DocumentStorage storage = getStorage();
            storage.storeSignedXml(gre.getBusinessCode(), gre.getSeries(), gre.getNumber(), signResult.signedXml());

            // Send via SUNAT GRE REST API
            SunatGreClient greClient = greClient(tenant);

            String filename = gre.getIssuerRuc() + "-09-" + gre.getFullNumber();
            SunatGreClient.GreSendResult result = greClient.send(filename, signResult.signedXml());

            if (result.success()) {
                gre.setStatus(DocumentStatus.SENT);
                gre.setSunatTicket(result.ticket());
                gre.setSunatResponse("Enviado a SUNAT; pendiente de CDR");
            } else {
                gre.setStatus(DocumentStatus.ERROR);
                gre.setSunatResponse(result.error());
            }
        } catch (Exception e) {
            LOG.error("GRE emission failed: " + gre.getFullNumber(), e);
            gre.setStatus(DocumentStatus.ERROR);
            gre.setSunatResponse(e.getMessage());
        }

        em.merge(gre);

        LOG.infof("GRE emitted: %s for %s", gre.getFullNumber(), gre.getBusinessCode());
        return gre;
    }

    public Optional<DispatchGuide> findById(UUID id) {
        return Optional.ofNullable(em.find(DispatchGuide.class, id));
    }

    public Optional<DispatchGuide> findById(UUID id, String businessCode) {
        return em.createQuery(
                "SELECT g FROM DispatchGuide g WHERE g.id = :id AND g.businessCode = :bc", DispatchGuide.class)
            .setParameter("id", id)
            .setParameter("bc", businessCode)
            .getResultStream()
            .findFirst();
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
                "SELECT g FROM DispatchGuide g WHERE g.sunatTicket IS NOT NULL " +
                    "AND g.status IN (:sent, :pending, :error) " +
                    "AND (g.nextRetryAt IS NULL OR g.nextRetryAt <= :now) ORDER BY g.updatedAt",
                DispatchGuide.class)
            .setParameter("sent", DocumentStatus.SENT)
            .setParameter("pending", DocumentStatus.PENDING)
            .setParameter("error", DocumentStatus.ERROR)
            .setParameter("now", Instant.now())
            .setMaxResults(Math.min(Math.max(limit, 1), 100))
            .getResultList();
    }

    @Transactional
    public Optional<DispatchGuide> checkStatus(UUID id, String businessCode) {
        Optional<DispatchGuide> found = findById(id, businessCode);
        if (found.isEmpty()) return Optional.empty();
        DispatchGuide guide = found.get();
        if (guide.getSunatTicket() == null || guide.getSunatTicket().isBlank()) return found;
        Tenant tenant = tenantService.resolve(businessCode);
        SunatGreClient client = greClient(tenant);
        var result = client.checkStatus(guide.getSunatTicket());
        if (result.accepted()) {
            guide.setStatus(DocumentStatus.ACCEPTED);
            guide.setSunatResponse(result.message());
            guide.setNextRetryAt(null);
            if (result.cdrXml() != null) {
                getStorage().storeCdr(guide.getBusinessCode(), guide.getSeries(), guide.getNumber(), result.cdrXml());
            }
        } else if ("98".equals(result.code()) || "PENDING".equalsIgnoreCase(result.code())) {
            guide.setStatus(DocumentStatus.PENDING);
            guide.setSunatResponse(result.message());
            guide.setNextRetryAt(Instant.now().plusSeconds(300));
        } else {
            guide.setStatus(DocumentStatus.ERROR);
            guide.setSunatResponse(result.message());
            guide.setRetryCount(guide.getRetryCount() + 1);
            guide.setNextRetryAt(guide.getRetryCount() < 3 ? Instant.now().plusSeconds(900) : null);
        }
        em.merge(guide);
        return Optional.of(guide);
    }

    public Optional<String> getSignedXml(UUID id, String businessCode) {
        return findById(id, businessCode).flatMap(guide -> getStorage().getSignedXml(
            guide.getBusinessCode(), guide.getSeries(), guide.getNumber()));
    }

    public Optional<String> getCdr(UUID id, String businessCode) {
        return findById(id, businessCode).flatMap(guide -> getStorage().getCdr(
            guide.getBusinessCode(), guide.getSeries(), guide.getNumber()));
    }

    private SunatGreClient greClient(Tenant tenant) {
        boolean isBeta = "BETA".equals(tenant.getEnvironment());
        String solUser = greSolUser(tenant.getIssuerRuc(), tenant.getSolUser());
        return new SunatGreClient(greClientId.orElse(""), greClientSecret.orElse(""), solUser,
            secretResolver.resolve(tenant.getSolPassword()), isBeta);
    }

    public boolean greCredentialsConfigured() {
        return greClientId.filter(value -> !value.isBlank() && !"not-configured".equals(value)).isPresent()
            && greClientSecret.filter(value -> !value.isBlank() && !"not-configured".equals(value)).isPresent();
    }

    static String greSolUser(String ruc, String user) {
        return user.startsWith(ruc) ? user : ruc + user;
    }

    private DocumentStorage getStorage() {
        String configured = storageEncryptionKey.orElse(null);
        String resolved = configured == null || configured.isBlank() ? null : secretResolver.resolve(configured);
        return new DocumentStorage(storagePath, resolved);
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
