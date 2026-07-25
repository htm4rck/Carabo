package io.carabo.app.rest;

import io.carabo.app.service.TenantService;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.SendMode;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/tenants")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tenants", description = "Gestión de empresas emisoras")
public class TenantResource {

    @Inject
    EntityManager em;

    @Inject
    TenantService tenantService;

    @POST
    @Transactional
    @Operation(summary = "Crear tenant", description = "Registra una nueva empresa emisora con API key auto-generada")
    public Response create(CreateTenantRequest req) {
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
        tenant.setCountryCode(req.countryCode != null ? req.countryCode : "PE");
        tenant.setMaxDocumentsPerDay(req.maxDocumentsPerDay != null ? req.maxDocumentsPerDay : 1000);
        // Generate API key
        tenant.setApiKey(java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        em.persist(tenant);
        return Response.status(201).entity(tenant).build();
    }

    @GET
    @Path("/{businessCode}")
    @Operation(summary = "Obtener tenant por businessCode")
    public Response getByBusinessCode(@PathParam("businessCode") String businessCode) {
        return tenantService.findByBusinessCode(businessCode)
            .map(t -> Response.ok(t).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Operation(summary = "Listar todos los tenants")
    public Response listAll() {
        var tenants = em.createQuery("SELECT t FROM Tenant t ORDER BY t.businessCode", Tenant.class)
            .getResultList();
        return Response.ok(tenants).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Operation(summary = "Actualizar tenant")
    public Response update(@PathParam("id") UUID id, UpdateTenantRequest req) {
        Tenant tenant = em.find(Tenant.class, id);
        if (tenant == null) return Response.status(404).build();

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

        em.merge(tenant);
        tenantService.evict(tenant.getBusinessCode());
        return Response.ok(tenant).build();
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
}
