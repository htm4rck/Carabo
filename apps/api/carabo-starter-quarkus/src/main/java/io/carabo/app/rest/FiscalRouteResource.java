package io.carabo.app.rest;
import io.carabo.app.security.TenantContext;
import io.carabo.app.service.FiscalRoutingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
@Path("/fiscal-routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FiscalRouteResource {
    @Inject TenantContext context;
    @Inject FiscalRoutingService routes;
    @GET public FiscalRoutingService.Schedule get() {
        if(!context.canManage(context.tenant())) throw new ForbiddenException();
        return routes.get(context.businessCode());
    }
    @PUT public FiscalRoutingService.Schedule save(FiscalRoutingService.Save input) {
        if(!context.canManage(context.tenant())) throw new ForbiddenException();
        return routes.save(context.tenant(),input,context.userCode());
    }
}
