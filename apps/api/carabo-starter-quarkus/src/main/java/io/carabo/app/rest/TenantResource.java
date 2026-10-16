package io.carabo.app.rest;

import io.carabo.app.service.TenantService;
import io.carabo.app.service.CertificateService;
import io.carabo.app.service.FiscalReadinessService;
import io.carabo.app.security.ApiKeyService;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.SendMode;
import io.carabo.core.country.CountryStrategyFactory;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.UUID;
import java.time.Instant;

@Path("/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tenants", description = "Gestión de empresas emisoras")
public class TenantResource {

    @Inject
    EntityManager em;

    @Inject
    TenantService tenantService;

    @Inject
    ApiKeyService apiKeyService;

    @Inject
    CertificateService certificateService;

    @Inject
    FiscalReadinessService fiscalReadinessService;

    @POST
    @Transactional
    @Operation(summary = "Crear tenant", description = "Registra una nueva empresa emisora con API key auto-generada")
    public Response create(CreateTenantRequest req) {
        String validationError = validateCreate(req);
        if (validationError != null) return Response.status(400).entity(new ErrorResponse(validationError)).build();
        Tenant tenant = new Tenant();
        tenant.setBusinessCode(req.businessCode);
        tenant.setBusinessName(req.businessName);
        tenant.setIssuerRuc(req.issuerRuc);
        tenant.setIssuerName(req.issuerName);
        tenant.setIssuerTrade(req.issuerTrade);
        tenant.setIssuerAddress(req.issuerAddress);
        tenant.setIssuerUbigeo(req.issuerUbigeo);
        tenant.setSendMode(req.sendMode != null ? SendMode.valueOf(req.sendMode) : SendMode.SUNAT_DIRECT);
        tenant.setSunatEndpoint(req.sunatEndpoint);
        tenant.setSolUser(req.solUser);
        tenant.setSolPassword(req.solPassword);
        tenant.setOseBaseUrl(req.oseBaseUrl);
        tenant.setOseApiToken(req.oseApiToken);
        tenant.setCertificateFile(req.certificateFile);
        tenant.setCertificatePassword(req.certificatePassword);
        tenant.setInvoiceSeries(req.invoiceSeries != null ? req.invoiceSeries : "F001");
        tenant.setReceiptSeries(req.receiptSeries != null ? req.receiptSeries : "B001");
        tenant.setCreditNoteSeries(req.creditNoteSeries != null ? req.creditNoteSeries : "FC01");
        tenant.setDebitNoteSeries(req.debitNoteSeries != null ? req.debitNoteSeries : "FD01");
        tenant.setEnvironment(req.environment != null ? req.environment : "BETA");
        tenant.setWebhookUrl(req.webhookUrl);
        tenant.setWebhookSecret(req.webhookSecret);
        tenant.setCountryCode(req.countryCode != null ? req.countryCode.trim().toUpperCase() : "PE");
        tenant.setMaxDocumentsPerDay(req.maxDocumentsPerDay != null ? req.maxDocumentsPerDay : 1000);
        String apiKey = apiKeyService.generate();
        tenant.setApiKeyHash(apiKeyService.hash(apiKey));
        tenant.setApiKeyRotatedAt(Instant.now());
        em.persist(tenant);
        return Response.status(201).entity(new CreatedTenantResponse(toResponse(tenant), apiKey)).build();
    }

    @GET
    @Path("/{businessCode}")
    @Operation(summary = "Obtener tenant por businessCode")
    public Response getByBusinessCode(@PathParam("businessCode") String businessCode) {
        return tenantService.findByBusinessCode(businessCode)
            .map(t -> Response.ok(toResponse(t)).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Path("/{businessCode}/readiness")
    @Operation(summary = "Preflight fiscal del tenant", description = "Comprueba la preparación para SUNAT sin exponer secretos")
    public Response readiness(@PathParam("businessCode") String businessCode) {
        return tenantService.findByBusinessCode(businessCode)
            .map(tenant -> Response.ok(fiscalReadinessService.assess(tenant)).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Operation(summary = "Listar todos los tenants")
    public Response listAll() {
        var tenants = em.createQuery("SELECT t FROM Tenant t ORDER BY t.businessCode", Tenant.class)
            .getResultList();
        return Response.ok(tenants.stream().map(this::toResponse).toList()).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Actualizar tenant")
    public Response update(@PathParam("id") UUID id, UpdateTenantRequest req) {
        Tenant tenant = em.find(Tenant.class, id);
        if (tenant == null) return Response.status(404).build();

        String validationError = validateUpdate(req);
        if (validationError != null) return Response.status(400).entity(new ErrorResponse(validationError)).build();

        if (req.businessName != null) tenant.setBusinessName(req.businessName);
        if (req.issuerRuc != null) tenant.setIssuerRuc(req.issuerRuc);
        if (req.issuerName != null) tenant.setIssuerName(req.issuerName);
        if (req.issuerTrade != null) tenant.setIssuerTrade(req.issuerTrade);
        if (req.issuerAddress != null) tenant.setIssuerAddress(req.issuerAddress);
        if (req.issuerUbigeo != null) tenant.setIssuerUbigeo(req.issuerUbigeo);
        if (req.sendMode != null) tenant.setSendMode(SendMode.valueOf(req.sendMode));
        if (req.sunatEndpoint != null) tenant.setSunatEndpoint(req.sunatEndpoint);
        if (req.solUser != null) tenant.setSolUser(req.solUser);
        if (req.solPassword != null) tenant.setSolPassword(req.solPassword);
        if (req.oseBaseUrl != null) tenant.setOseBaseUrl(req.oseBaseUrl);
        if (req.oseApiToken != null) tenant.setOseApiToken(req.oseApiToken);
        if (req.certificateFile != null) tenant.setCertificateFile(req.certificateFile);
        if (req.certificatePassword != null) tenant.setCertificatePassword(req.certificatePassword);
        if (req.invoiceSeries != null) tenant.setInvoiceSeries(req.invoiceSeries);
        if (req.receiptSeries != null) tenant.setReceiptSeries(req.receiptSeries);
        if (req.creditNoteSeries != null) tenant.setCreditNoteSeries(req.creditNoteSeries);
        if (req.debitNoteSeries != null) tenant.setDebitNoteSeries(req.debitNoteSeries);
        if (req.environment != null) tenant.setEnvironment(req.environment);
        if (req.webhookUrl != null) tenant.setWebhookUrl(req.webhookUrl);
        if (req.countryCode != null) tenant.setCountryCode(req.countryCode.trim().toUpperCase());
        if (req.maxDocumentsPerDay != null) tenant.setMaxDocumentsPerDay(req.maxDocumentsPerDay);

        em.merge(tenant);
        tenantService.evict(tenant.getBusinessCode());
        return Response.ok(toResponse(tenant)).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Desactivar tenant")
    public Response deactivate(@PathParam("id") UUID id) {
        Tenant tenant = em.find(Tenant.class, id);
        if (tenant == null) return Response.status(404).build();
        tenant.setActive(false);
        em.merge(tenant);
        tenantService.evict(tenant.getBusinessCode());
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/regenerate-key")
    @Transactional
    @Operation(summary = "Rotar API key", description = "Revoca inmediatamente la clave anterior y retorna la nueva una sola vez")
    public Response regenerateApiKey(@PathParam("id") UUID id) {
        Tenant tenant = em.find(Tenant.class, id);
        if (tenant == null) return Response.status(404).build();

        String apiKey = apiKeyService.generate();
        tenant.setApiKeyHash(apiKeyService.hash(apiKey));
        tenant.setApiKeyRotatedAt(Instant.now());
        em.merge(tenant);
        tenantService.evict(tenant.getBusinessCode());
        return Response.ok(new RotatedApiKeyResponse(apiKey, tenant.getApiKeyRotatedAt())).build();
    }

    @POST
    @Path("/{id}/certificate")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @Operation(summary = "Subir certificado", description = "Valida y guarda un PKCS#12 usando una contraseña del secret store")
    public Response uploadCertificate(@PathParam("id") UUID id,
                                      @RestForm("certificate") FileUpload certificate,
                                      @RestForm("passwordReference") String passwordReference) {
        Tenant tenant = em.find(Tenant.class, id);
        if (tenant == null) return Response.status(404).build();
        try {
            var metadata = certificateService.validateAndStore(id,
                certificate != null ? certificate.fileName() : null,
                certificate != null ? certificate.uploadedFile() : null,
                certificate != null ? certificate.size() : 0,
                passwordReference);
            tenant.setCertificateFile(metadata.fileName());
            tenant.setCertificatePassword(metadata.passwordReference());
            tenant.setCertificateAlias(metadata.alias());
            tenant.setCertificateValidFrom(metadata.validFrom());
            tenant.setCertificateValidTo(metadata.validTo());
            em.merge(tenant);
            tenantService.evict(tenant.getBusinessCode());
            return Response.ok(toResponse(tenant)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(400).entity(new ErrorResponse(e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(503).entity(new ErrorResponse("El secret store o el volumen de certificados no está disponible")).build();
        }
    }

    static String validateUpdate(UpdateTenantRequest req) {
        if (req == null) return "La configuración es obligatoria";
        if (req.issuerRuc != null && !req.issuerRuc.matches("\\d{11}")) return "El RUC debe contener 11 dígitos";
        if (req.issuerUbigeo != null && !req.issuerUbigeo.isBlank() && !req.issuerUbigeo.matches("\\d{6}")) {
            return "El ubigeo debe contener 6 dígitos";
        }
        if (req.sendMode != null) {
            try { SendMode.valueOf(req.sendMode); }
            catch (IllegalArgumentException e) { return "El canal de envío no es válido"; }
        }
        if (req.environment != null && !java.util.Set.of("BETA", "HOMOLOGATION", "PRODUCTION").contains(req.environment)) {
            return "El ambiente no es válido";
        }
        if (req.maxDocumentsPerDay != null && (req.maxDocumentsPerDay < 1 || req.maxDocumentsPerDay > 100000)) {
            return "El máximo diario debe estar entre 1 y 100000";
        }
        if (req.solPassword != null && !req.solPassword.startsWith("secret://")) {
            return "La contraseña SOL debe usar una referencia secret://";
        }
        if (req.certificatePassword != null && !req.certificatePassword.startsWith("secret://")) {
            return "La contraseña del certificado debe usar una referencia secret://";
        }
        if (req.oseApiToken != null && !req.oseApiToken.startsWith("secret://")) {
            return "El token OSE debe usar una referencia secret://";
        }
        if (req.countryCode != null && !CountryStrategyFactory.isSupported(req.countryCode)) {
            return "Solo PE está habilitado; otros países requieren compliance y homologación antes de activarse";
        }
        if ("OSE".equals(req.sendMode)) {
            if (!secureHttpUrl(req.oseBaseUrl)) return "OSE requiere una URL HTTPS válida";
            if (req.oseApiToken == null || !req.oseApiToken.startsWith("secret://")) {
                return "OSE requiere un token referenciado mediante secret://";
            }
        }
        return null;
    }

    static String validateCreate(CreateTenantRequest req) {
        if (req == null) return "La configuración es obligatoria";
        UpdateTenantRequest update = new UpdateTenantRequest();
        update.issuerRuc = req.issuerRuc;
        update.issuerUbigeo = req.issuerUbigeo;
        update.sendMode = req.sendMode;
        update.environment = req.environment;
        update.maxDocumentsPerDay = req.maxDocumentsPerDay;
        update.solPassword = req.solPassword;
        update.certificatePassword = req.certificatePassword;
        update.oseApiToken = req.oseApiToken;
        update.oseBaseUrl = req.oseBaseUrl;
        update.countryCode = req.countryCode != null ? req.countryCode : "PE";
        return validateUpdate(update);
    }

    private static boolean secureHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            var uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException error) { return false; }
    }

    public static class CreateTenantRequest {
        public String businessCode;
        public String businessName;
        public String issuerRuc;
        public String issuerName;
        public String issuerTrade;
        public String issuerAddress;
        public String issuerUbigeo;
        public String sendMode;
        public String sunatEndpoint;
        public String solUser;
        public String solPassword;
        public String oseBaseUrl;
        public String oseApiToken;
        public String certificateFile;
        public String certificatePassword;
        public String invoiceSeries;
        public String receiptSeries;
        public String creditNoteSeries;
        public String debitNoteSeries;
        public String environment;
        public String webhookUrl;
        public String webhookSecret;
        public String countryCode;
        public Integer maxDocumentsPerDay;
    }

    public static class UpdateTenantRequest extends CreateTenantRequest {}

    private TenantResponse toResponse(Tenant tenant) {
        var response = new TenantResponse();
        response.id = tenant.getId();
        response.businessCode = tenant.getBusinessCode();
        response.businessName = tenant.getBusinessName();
        response.issuerRuc = tenant.getIssuerRuc();
        response.issuerName = tenant.getIssuerName();
        response.issuerTrade = tenant.getIssuerTrade();
        response.issuerAddress = tenant.getIssuerAddress();
        response.issuerUbigeo = tenant.getIssuerUbigeo();
        response.sendMode = tenant.getSendMode() != null ? tenant.getSendMode().name() : null;
        response.sunatEndpoint = tenant.getSunatEndpoint();
        response.oseBaseUrl = tenant.getOseBaseUrl();
        response.certificateFile = tenant.getCertificateFile();
        response.certificateValidFrom = tenant.getCertificateValidFrom();
        response.certificateValidTo = tenant.getCertificateValidTo();
        response.certificateAlias = tenant.getCertificateAlias();
        response.invoiceSeries = tenant.getInvoiceSeries();
        response.receiptSeries = tenant.getReceiptSeries();
        response.creditNoteSeries = tenant.getCreditNoteSeries();
        response.debitNoteSeries = tenant.getDebitNoteSeries();
        response.environment = tenant.getEnvironment();
        response.webhookUrl = tenant.getWebhookUrl();
        response.countryCode = tenant.getCountryCode();
        response.maxDocumentsPerDay = tenant.getMaxDocumentsPerDay();
        response.active = tenant.isActive();
        response.createdAt = tenant.getCreatedAt();
        response.updatedAt = tenant.getUpdatedAt();
        response.apiKeyRotatedAt = tenant.getApiKeyRotatedAt();
        return response;
    }

    /** Public administration view. Credentials and password fields are omitted. */
    public static class TenantResponse {
        public UUID id;
        public String businessCode;
        public String businessName;
        public String issuerRuc;
        public String issuerName;
        public String issuerTrade;
        public String issuerAddress;
        public String issuerUbigeo;
        public String sendMode;
        public String sunatEndpoint;
        public String oseBaseUrl;
        public String certificateFile;
        public java.time.LocalDate certificateValidFrom;
        public java.time.LocalDate certificateValidTo;
        public String certificateAlias;
        public String invoiceSeries;
        public String receiptSeries;
        public String creditNoteSeries;
        public String debitNoteSeries;
        public String environment;
        public String webhookUrl;
        public String countryCode;
        public Integer maxDocumentsPerDay;
        public boolean active;
        public Instant createdAt;
        public Instant updatedAt;
        public Instant apiKeyRotatedAt;
    }

    /** API key is returned once at creation and never by read/update endpoints. */
    public record CreatedTenantResponse(TenantResponse tenant, String apiKey) {}
    public record RotatedApiKeyResponse(String apiKey, Instant rotatedAt) {}
    public record ErrorResponse(String error) {}
}
