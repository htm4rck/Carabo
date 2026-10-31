package io.carabo.app.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.app.client.LayoutClient;
import io.carabo.app.security.TenantContext;
import io.carabo.app.service.DispatchGuidePayloadValidator;
import io.carabo.app.service.DispatchGuideService;
import io.carabo.app.service.FiscalClock;
import io.carabo.core.dto.EmitDispatchGuideRequest;
import io.carabo.core.entity.gre.DispatchGuide;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/dispatch-guides")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dispatch Guides", description = "Guía de Remisión Electrónica GRE 2.0")
public class DispatchGuideResource {

  @Inject DispatchGuideService service;

  @Inject LayoutClient layoutClient;

  @Inject TenantContext tenantContext;

  @Inject DispatchGuidePayloadValidator payloadValidator;

  @Inject FiscalClock fiscalClock;

  @Inject ObjectMapper objectMapper;

  @GET
  @Path("/readiness")
  public Response readiness() {
    String environment =
        DispatchGuideService.greEnvironmentBlocker(tenantContext.tenant().getEnvironment());
    boolean ready =
        environment == null && service.greCredentialsConfigured(tenantContext.businessCode());
    return Response.ok(
            java.util.Map.of(
                "ready",
                ready,
                "businessCode",
                tenantContext.businessCode(),
                "message",
                environment != null
                    ? environment
                    : ready
                        ? "Credenciales OAuth de GRE configuradas; valida emisor y documento antes"
                              + " de emitir"
                        : "Configura las credenciales GRE del emisor en Carabo. Las solicitudes"
                              + " pueden guardarse, pero no habilitan el traslado"))
        .build();
  }

  @POST
  @Path("/validate")
  @Operation(
      summary = "Validar payload GRE",
      description = "Compara el JSON GRE 2.0 sin emitir ni reservar correlativo")
  public Response validate(
      JsonNode payload, @HeaderParam("Idempotency-Key") String idempotencyKey) {
    return Response.ok(payloadValidator.validate(payload, idempotencyKey, fiscalClock.today()))
        .build();
  }

  @POST
  @Path("/emit")
  @Operation(summary = "Emitir guía de remisión electrónica")
  public Response emit(
      EmitDispatchGuideRequest request, @HeaderParam("Idempotency-Key") String idempotencyKey) {
    if (request == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new ErrorResponse("INVALID_REQUEST", "El cuerpo de la guía es obligatorio"))
          .build();
    }
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    if (normalizedKey != null) {
      if (normalizedKey.length() > 100) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("Idempotency-Key no puede exceder 100 caracteres")
            .build();
      }
      String externalId = normalizeIdempotencyKey(request.getExternalId());
      if (externalId != null && !externalId.equals(normalizedKey)) {
        return Response.status(Response.Status.CONFLICT)
            .entity("Idempotency-Key y externalId deben coincidir cuando ambos están presentes")
            .build();
      }
      request.setExternalId(normalizedKey);
    }
    request.setBusinessCode(tenantContext.businessCode());
    var report =
        payloadValidator.validate(
            objectMapper.valueToTree(request), normalizedKey, fiscalClock.today());
    if (!report.valid()) return Response.status(400).entity(report).build();
    DispatchGuide gre = service.emit(request);
    return Response.status(201).entity(toSummary(gre)).build();
  }

  private static String normalizeIdempotencyKey(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  @GET
  @Path("/states")
  public Response states(@QueryParam("id") java.util.List<UUID> ids) {
    return Response.ok(
            service.findStates(ids, tenantContext.businessCode()).stream()
                .map(this::toSummary)
                .toList())
        .build();
  }

  @GET
  @Path("/{id}")
  @Operation(summary = "Obtener GRE por ID")
  public Response getById(@PathParam("id") UUID id) {
    return service
        .findById(id, tenantContext.businessCode())
        .map(g -> Response.ok(toSummary(g)).build())
        .orElse(Response.status(404).build());
  }

  @GET
  @Operation(summary = "Listar GREs por empresa")
  public Response list(
      @QueryParam("businessCode") String businessCode,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("limit") @DefaultValue("50") int limit) {
    var guides = service.findByBusiness(tenantContext.businessCode(), offset, Math.min(limit, 200));
    return Response.ok(guides.stream().map(this::toSummary).toList()).build();
  }

  @GET
  @Path("/{id}/pdf")
  @Produces("application/pdf")
  public Response getPdf(
      @PathParam("id") UUID id, @HeaderParam("Authorization") String authorization) {
    return service
        .findById(id, tenantContext.businessCode())
        .map(
            gre -> {
              if (!service.printable(gre))
                return Response.status(409)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(
                        new ErrorResponse(
                            "GRE_NOT_TRANSPORT_AUTHORIZED",
                            "PDF de traslado bloqueado: requiere una GRE de producción con CDR"
                                + " aceptada. Una solicitud pendiente, rechazada o de prueba no"
                                + " sustenta el traslado"))
                    .build();
              byte[] pdf = layoutClient.generatePdf(gre, "A4", authorization);
              return pdf != null
                  ? Response.ok(pdf)
                      .header(
                          "Content-Disposition",
                          "inline; filename=\"" + gre.getFullNumber() + ".pdf\"")
                      .build()
                  : Response.status(502)
                      .entity("No se pudo generar la representación impresa")
                      .build();
            })
        .orElse(Response.status(404).build());
  }

  @POST
  @Path("/{id}/status")
  @Operation(summary = "Consultar ticket y actualizar estado/CDR de la GRE")
  public Response checkStatus(@PathParam("id") UUID id) {
    return service
        .checkStatus(id, tenantContext.businessCode())
        .map(g -> Response.ok(toSummary(g)).build())
        .orElse(Response.status(404).build());
  }

  @POST
  @Path("/{id}/retry")
  @Operation(summary = "Programar reintento de la misma GRE sin duplicar el documento")
  public Response retry(@PathParam("id") UUID id) {
    try {
      return service
          .requestRetry(
              id,
              tenantContext.businessCode(),
              tenantContext.userCode() != null
                  ? tenantContext.userCode()
                  : tenantContext.producerCode())
          .map(guide -> Response.accepted(toSummary(guide)).build())
          .orElse(Response.status(404).build());
    } catch (ClientErrorException error) {
      return Response.status(409)
          .entity(new ErrorResponse("GRE_RETRY_BLOCKED", error.getMessage()))
          .build();
    }
  }

  @GET
  @Path("/{id}/xml")
  @Produces(MediaType.APPLICATION_XML)
  public Response getXml(@PathParam("id") UUID id) {
    return service
        .getSignedXml(id, tenantContext.businessCode())
        .map(
            xml ->
                Response.ok(xml)
                    .header("Content-Disposition", "attachment; filename=\"gre.xml\"")
                    .build())
        .orElse(Response.status(404).build());
  }

  @GET
  @Path("/{id}/cdr")
  @Produces(MediaType.APPLICATION_XML)
  public Response getCdr(@PathParam("id") UUID id) {
    return service
        .getCdr(id, tenantContext.businessCode())
        .map(
            xml ->
                Response.ok(xml)
                    .header("Content-Disposition", "attachment; filename=\"cdr-gre.xml\"")
                    .build())
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
    s.sunatResponseCode = g.getSunatResponseCode();
    s.transportAuthorized = service.printable(g);
    s.environment = g.getEnvironment();
    s.updatedAt = g.getUpdatedAt();
    s.createdAt = g.getCreatedAt();
    s.nextRetryAt = g.getNextRetryAt();
    s.retryCount = g.getRetryCount();
    s.maxRetryCount = 8;
    s.retryBlocker = service.retryBlocker(g);
    s.automaticRetry = s.retryBlocker == null && s.nextRetryAt != null;
    boolean retryPermission =
        tenantContext.isAdministrator()
            || tenantContext.roles().contains("SUPERVISOR_Carabo")
            || tenantContext.producerScopes().contains("DOCUMENT_EMIT");
    s.canRetry = s.retryBlocker == null && retryPermission;
    s.retryAction =
        s.retryBlocker == null ? (g.getSunatTicket() == null ? "SEND" : "CHECK_STATUS") : null;
    if (s.retryBlocker == null && !retryPermission)
      s.retryBlocker = "Solicita el reintento a un supervisor o administrador de Carabo";
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
    public String sunatResponseCode;
    public boolean transportAuthorized;
    public String environment;
    public java.time.Instant updatedAt;
    public java.time.Instant createdAt;
    public java.time.Instant nextRetryAt;
    public int retryCount;
    public int maxRetryCount;
    public String retryBlocker;
    public String retryAction;
    public boolean canRetry;
    public boolean automaticRetry;
  }

  public record ErrorResponse(String code, String message) {}
}
