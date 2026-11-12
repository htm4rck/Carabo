package io.carabo.app.rest;

import io.carabo.app.security.TenantContext;
import io.carabo.application.producer.ProducerUseCases;
import io.carabo.application.producer.ProducerContracts.CreateProducer;
import io.carabo.application.producer.ProducerContracts.ProducerSummary;
import io.carabo.domain.BusinessScope;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Path("/integrations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Producer integrations", description = "Identidades máquina independientes por aplicación productora")
public class ProducerIntegrationResource {
    @Inject TenantContext tenantContext;
    @Inject ProducerUseCases producers;

    @GET
    @Operation(summary = "Listar aplicaciones productoras del tenant activo")
    public List<ProducerSummary> list() {
        return producers.list(new BusinessScope(tenantContext.businessCode()));
    }

    @POST
    @Operation(summary = "Crear productor", description = "La API key se entrega una sola vez")
    public Response create(CreateProducerRequest request) {
        if (request == null) return Response.status(400).entity(new ErrorResponse("Solicitud obligatoria")).build();
        var created = producers.create(new BusinessScope(tenantContext.businessCode()),
            new CreateProducer(request.code, request.name, request.description, request.scopes));
        return Response.status(201).entity(created).build();
    }

    @POST @Path("/{id}/rotate")
    @Operation(summary = "Rotar credencial", description = "Revoca inmediatamente la clave anterior")
    public Response rotate(@PathParam("id") UUID id) {
        return producers.rotate(new BusinessScope(tenantContext.businessCode()), id)
            .map(created -> Response.ok(created).build())
            .orElse(Response.status(404).build());
    }

    @POST @Path("/{id}/status")
    @Operation(summary = "Activar o revocar productor")
    public Response status(@PathParam("id") UUID id, StatusRequest request) {
        if (request == null) return Response.status(400).entity(new ErrorResponse("Solicitud obligatoria")).build();
        return producers.setActive(new BusinessScope(tenantContext.businessCode()), id, request.active)
            .map(producer -> Response.ok(producer).build())
            .orElse(Response.status(404).build());
    }

    public static class CreateProducerRequest { public String code; public String name; public String description; public Set<String> scopes; }
    public static class StatusRequest { public boolean active; }
    public record ErrorResponse(String error) {}
}
