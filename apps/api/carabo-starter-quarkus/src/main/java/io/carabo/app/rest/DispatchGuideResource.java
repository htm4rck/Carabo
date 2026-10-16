package io.carabo.app.rest;

import io.carabo.app.client.LayoutClient;
import io.carabo.app.service.DispatchGuidePayloadValidator;
import io.carabo.app.service.DispatchGuideService;
import io.carabo.app.service.FiscalClock;
import io.carabo.app.security.TenantContext;
import io.carabo.core.dto.EmitDispatchGuideRequest;
import io.carabo.core.entity.gre.DispatchGuide;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/dispatch-guides")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dispatch Guides", description = "Guía de Remisión Electrónica GRE 2.0")
public class DispatchGuideResource {

    @Inject
    DispatchGuideService service;

    @Inject
    LayoutClient layoutClient;

    @Inject
    TenantContext tenantContext;

    @Inject
    DispatchGuidePayloadValidator payloadValidator;

    @Inject
    FiscalClock fiscalClock;

    @POST
    @Path("/validate")
    @Operation(summary = "Validar payload GRE", description = "Compara el JSON GRE 2.0 sin emitir ni reservar correlativo")
    public Response validate(JsonNode payload, @HeaderParam("Idempotency-Key") String idempotencyKey) {
        return Response.ok(payloadValidator.validate(payload, idempotencyKey, fiscalClock.today())).build();
    }

    @POST
    @Path("/emit")
    @Operation(summary = "Emitir guía de remisión electrónica")
    public Response emit(EmitDispatchGuideRequest request, @HeaderParam("Idempotency-Key") String idempotencyKey) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("INVALID_REQUEST", "El cuerpo de la guía es obligatorio")).build();
        }
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            if (normalizedKey.length() > 100) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Idempotency-Key no puede exceder 100 caracteres").build();
            }
            String externalId = normalizeIdempotencyKey(request.getExternalId());
            if (externalId != null && !externalId.equals(normalizedKey)) {
                return Response.status(Response.Status.CONFLICT)
                    .entity("Idempotency-Key y externalId deben coincidir cuando ambos están presentes").build();
            }
            request.setExternalId(normalizedKey);
        }
        request.setBusinessCode(tenantContext.businessCode());
        DispatchGuide gre = service.emit(request);
        return Response.status(201).entity(toSummary(gre)).build();
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener GRE por ID")
    public Response getById(@PathParam("id") UUID id) {
        return service.findById(id, tenantContext.businessCode())
            .map(g -> Response.ok(toSummary(g)).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Operation(summary = "Listar GREs por empresa")
    public Response list(
        @QueryParam("businessCode") String businessCode,
        @QueryParam("offset") @DefaultValue("0") int offset,
        @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        var guides = service.findByBusiness(tenantContext.businessCode(), offset, Math.min(limit, 200));
        return Response.ok(guides.stream().map(this::toSummary).toList()).build();
    }

    @GET
    @Path("/{id}/pdf")
    @Produces("application/pdf")
    public Response getPdf(@PathParam("id") UUID id) {
        return service.findById(id, tenantContext.businessCode()).map(gre -> {
            byte[] pdf = layoutClient.generatePdf(gre, "A4");
            return pdf != null ? Response.ok(pdf)
                .header("Content-Disposition", "inline; filename=\"" + gre.getFullNumber() + ".pdf\"").build()
                : Response.status(502).entity("No se pudo generar la representación impresa").build();
        }).orElse(Response.status(404).build());
    }

    @POST
    @Path("/{id}/status")
    @Operation(summary = "Consultar ticket y actualizar estado/CDR de la GRE")
    public Response checkStatus(@PathParam("id") UUID id) {
        return service.checkStatus(id, tenantContext.businessCode())
            .map(g -> Response.ok(toSummary(g)).build()).orElse(Response.status(404).build());
    }

    @GET @Path("/{id}/xml") @Produces(MediaType.APPLICATION_XML)
    public Response getXml(@PathParam("id") UUID id) {
        return service.getSignedXml(id, tenantContext.businessCode())
            .map(xml -> Response.ok(xml).header("Content-Disposition", "attachment; filename=\"gre.xml\"").build())
            .orElse(Response.status(404).build());
    }

    @GET @Path("/{id}/cdr") @Produces(MediaType.APPLICATION_XML)
    public Response getCdr(@PathParam("id") UUID id) {
        return service.getCdr(id, tenantContext.businessCode())
            .map(xml -> Response.ok(xml).header("Content-Disposition", "attachment; filename=\"cdr-gre.xml\"").build())
            .orElse(Response.status(404).build());
    }

    private GreSummary toSummary(DispatchGuide g) {
        var s = new GreSummary();
        s.id = g.getId();
        s.fullNumber = g.getFullNumber();
        s.status = g.getStatus().name();
        s.issueDate = g.getIssueDate() != null ? g.getIssueDate().toString() : null;
        s.startDate = g.getStartDate() != null ? g.getStartDate().toString() : null;
        s.receiverName = g.getReceiverName();
        s.receiverDoc = g.getReceiverDoc();
        s.transportMode = g.getTransportMode();
        s.originAddress = g.getOriginAddress();
        s.destinationAddress = g.getDestinationAddress();
        s.transferReasonCode = g.getTransferReasonCode();
        s.businessCode = g.getBusinessCode();
        s.externalId = g.getExternalId();
        s.hashCode = g.getHashCode();
        s.ticket = g.getSunatTicket();
        s.sunatResponse = g.getSunatResponse();
        return s;
    }

    public static class GreSummary {
        public UUID id;
        public String fullNumber;
        public String status;
        public String issueDate;
        public String startDate;
        public String receiverName;
        public String receiverDoc;
        public String transportMode;
        public String originAddress;
        public String destinationAddress;
        public String transferReasonCode;
        public String businessCode;
        public String externalId;
        public String hashCode;
        public String ticket;
        public String sunatResponse;
    }

    public record ErrorResponse(String code, String message) {}
}
