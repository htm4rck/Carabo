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

    @ConfigProperty(name = "carabo.certs.base-path", defaultValue = "/app/certs")
    String certsBasePath;

    @ConfigProperty(name = "carabo.storage.base-path", defaultValue = "/app/storage/documents")
    String storagePath;

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

        DispatchGuide gre = new DispatchGuide();
        gre.setBusinessCode(request.getBusinessCode());
        gre.setSeries(request.getSeries() != null ? request.getSeries() : "T001");
        gre.setNumber(correlativeService.next(
            request.getBusinessCode(), CorrelativeService.Kind.DISPATCH_GUIDE, gre.getSeries()));
        gre.setIssueDate(request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now());
        gre.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());

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
            DocumentStorage storage = new DocumentStorage(storagePath);
            storage.storeSignedXml(gre.getBusinessCode(), gre.getSeries(), gre.getNumber(), signResult.signedXml());

            // Send via SUNAT GRE REST API
            boolean isBeta = "BETA".equals(tenant.getEnvironment());
            String solUser = tenant.getIssuerRuc() + tenant.getSolUser();
            SunatGreClient greClient = new SunatGreClient(
                greClientId.orElse(""), greClientSecret.orElse(""), solUser,
                secretResolver.resolve(tenant.getSolPassword()), isBeta);

            String filename = gre.getIssuerRuc() + "-09-" + gre.getFullNumber();
            SunatGreClient.GreSendResult result = greClient.send(filename, signResult.signedXml());

            if (result.success()) {
                gre.setStatus(DocumentStatus.SENT);
                gre.setSunatResponse("ticket:" + result.ticket());
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

    private XmlSigner createSigner(Tenant tenant) {
        try {
            String certPath = Path.of(certsBasePath, tenant.getCertificateFile()).toString();
            return new XmlSigner(new FileInputStream(certPath), secretResolver.resolve(tenant.getCertificatePassword()));
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
