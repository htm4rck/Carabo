package io.carabo.app.rest;

import io.carabo.app.security.TenantContext;
import io.carabo.app.service.ProducerIntegrationService;
import io.carabo.core.entity.ProducerIntegration;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Path("/integrations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Producer integrations", description = "Identidades máquina independientes por aplicación productora")
public class ProducerIntegrationResource {
    @Inject TenantContext tenantContext;
    @Inject ProducerIntegrationService producers;

    @GET
    @Operation(summary = "Listar aplicaciones productoras del tenant activo")
    public List<ProducerSummary> list() {
        return producers.list(tenantContext.businessCode()).stream().map(this::summary).toList();
    }

    @POST
    @Operation(summary = "Crear productor", description = "La API key se entrega una sola vez")
    public Response create(CreateProducerRequest request) {
        if (request == null) return Response.status(400).entity(new ErrorResponse("Solicitud obligatoria")).build();
        Set<String> scopes = request.scopes == null ? Set.of() : request.scopes;
        String error = ProducerIntegrationService.validate(request.code, request.name, scopes);
        if (error != null) return Response.status(400).entity(new ErrorResponse(error)).build();
        try {
            var created = producers.create(tenantContext.businessCode(), request.code, request.name, request.description, scopes);
            return Response.status(201).entity(new CredentialResponse(summary(created.producer()), created.apiKey())).build();
        } catch (PersistenceException duplicate) {
            return Response.status(409).entity(new ErrorResponse("Ya existe un productor con ese código")).build();
        }
    }

    @POST @Path("/{id}/rotate")
    @Operation(summary = "Rotar credencial", description = "Revoca inmediatamente la clave anterior")
    public Response rotate(@PathParam("id") UUID id) {
        return producers.rotate(id, tenantContext.businessCode())
            .map(created -> Response.ok(new CredentialResponse(summary(created.producer()), created.apiKey())).build())
            .orElse(Response.status(404).build());
    }

    @POST @Path("/{id}/status")
    @Operation(summary = "Activar o revocar productor")
    public Response status(@PathParam("id") UUID id, StatusRequest request) {
        if (request == null) return Response.status(400).entity(new ErrorResponse("Solicitud obligatoria")).build();
        return producers.setActive(id, tenantContext.businessCode(), request.active)
            .map(producer -> Response.ok(summary(producer)).build())
            .orElse(Response.status(404).build());
    }

    private ProducerSummary summary(ProducerIntegration producer) {
        return new ProducerSummary(producer.getId(), producer.getCode(), producer.getName(), producer.getDescription(),
            producers.scopes(producer), producer.getStatus(), health(producer), producer.getCreatedAt(), producer.getRotatedAt(),
            producer.getLastUsedAt(), producer.getLastSuccessAt(), producer.getLastFailureAt(), producer.getLastHttpStatus(),
            producer.getTotalRequests(), producer.getFailureRequests(), producers.documentCount(producer), producers.lastDocumentAt(producer));
    }

    private String health(ProducerIntegration producer) {
        if (!"ACTIVE".equals(producer.getStatus())) return "REVOKED";
        if (producer.getLastFailureAt() != null && (producer.getLastSuccessAt() == null || producer.getLastFailureAt().isAfter(producer.getLastSuccessAt()))) return "DEGRADED";
        if (producer.getLastUsedAt() == null) return "PENDING";
        if (producer.getLastUsedAt().isBefore(Instant.now().minusSeconds(7 * 86400L))) return "IDLE";
        return "HEALTHY";
    }

    public static class CreateProducerRequest { public String code; public String name; public String description; public Set<String> scopes; }
    public static class StatusRequest { public boolean active; }
    public record CredentialResponse(ProducerSummary producer, String apiKey) {}
    public record ProducerSummary(UUID id, String code, String name, String description, Set<String> scopes,
                                  String status, String health, Instant createdAt, Instant rotatedAt,
                                  Instant lastUsedAt, Instant lastSuccessAt, Instant lastFailureAt,
                                  Integer lastHttpStatus, long totalRequests, long failureRequests,
                                  long totalDocuments, Instant lastDocumentAt) {}
    public record ErrorResponse(String error) {}
}
