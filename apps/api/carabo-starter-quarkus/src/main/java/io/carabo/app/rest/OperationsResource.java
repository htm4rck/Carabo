package io.carabo.app.rest;

import io.carabo.app.security.TenantContext;
import io.carabo.app.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/operations")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Operations", description = "Trazabilidad operativa por tenant")
public class OperationsResource {
    @Inject NotificationService notificationService;
    @Inject TenantContext tenantContext;

    @GET @Path("/notifications")
    @Operation(summary = "Consultar entregas de callbacks y webhooks")
    public Response notifications(@QueryParam("limit") @DefaultValue("50") int limit) {
        return Response.ok(notificationService.list(tenantContext.businessCode(), limit)).build();
    }
}
