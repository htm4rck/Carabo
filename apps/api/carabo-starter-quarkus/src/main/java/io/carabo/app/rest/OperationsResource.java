package io.carabo.app.rest;

import io.carabo.app.security.TenantContext;
import io.carabo.app.service.NotificationService;
import io.carabo.app.service.AsyncMessageService;
import io.carabo.app.service.OperationalMetrics;
import io.carabo.core.dto.EmitDocumentRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import java.util.UUID;

@Path("/operations")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Operations", description = "Trazabilidad operativa por tenant")
public class OperationsResource {
    @Inject NotificationService notificationService;
    @Inject TenantContext tenantContext;
    @Inject AsyncMessageService asyncMessageService;
    @Inject OperationalMetrics metrics;
    @Inject @Channel("document-emit-out") Emitter<EmitDocumentRequest> documentEmitter;

    @GET @Path("/notifications")
    @Operation(summary = "Consultar entregas de callbacks y webhooks")
    public Response notifications(@QueryParam("limit") @DefaultValue("50") int limit) {
        return Response.ok(notificationService.list(tenantContext.businessCode(), limit)).build();
    }

    @POST @Path("/notifications/{id}/retry")
    @Operation(summary = "Reactivar una entrega fallida o pendiente")
    public Response retryNotification(@PathParam("id") String id) {
        return notificationService.retry(id, tenantContext.businessCode())
            ? Response.accepted().build() : Response.status(Response.Status.CONFLICT).build();
    }

    @GET @Path("/messages")
    @Operation(summary = "Consultar procesamiento asíncrono y mensajes enviados a DLQ")
    public Response messages(@QueryParam("status") String status,
                             @QueryParam("limit") @DefaultValue("50") int limit) {
        if (status != null && !status.isBlank()
            && !java.util.Set.of("RECEIVED", "PROCESSED", "DEAD", "REPLAYED").contains(status.toUpperCase())) {
            return Response.status(400).entity("Estado de mensaje inválido").build();
        }
        return Response.ok(asyncMessageService.list(tenantContext.businessCode(), status, limit)).build();
    }

    @POST @Path("/messages/{id}/replay")
    @Operation(summary = "Reprocesar de forma gobernada un mensaje fallido")
    public Response replay(@PathParam("id") UUID id) {
        var request = asyncMessageService.payloadForReplay(id, tenantContext.businessCode());
        if (request.isEmpty()) return Response.status(Response.Status.CONFLICT)
            .entity("El mensaje no existe, no pertenece al tenant o no está en estado DEAD").build();
        try {
            documentEmitter.send(request.get()).toCompletableFuture().join();
            asyncMessageService.markReplayed(id, tenantContext.businessCode());
            metrics.replay();
            return Response.accepted().build();
        } catch (RuntimeException error) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("RabbitMQ no confirmó el replay; el mensaje permanece disponible").build();
        }
    }
}
