package io.carabo.app.rest;

import io.carabo.app.security.TenantContext;
import io.carabo.app.service.FiscalStatusService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/fiscal-status")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Fiscal status", description = "Diagnóstico fiscal seguro y de sólo lectura")
public class FiscalStatusResource {
    @Inject TenantContext tenantContext;
    @Inject FiscalStatusService fiscalStatusService;

    @GET
    @Operation(summary = "Consultar preparación fiscal del tenant activo sin exponer secretos")
    public FiscalStatusService.FiscalStatus get() {
        return fiscalStatusService.assess(tenantContext.tenant());
    }
}
