package io.carabo.app.security;

import io.carabo.app.service.TenantService;
import io.carabo.core.entity.Tenant;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Set;

@Provider
public class ApiKeyFilter implements ContainerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/metrics", "/openapi");

    @Inject
    TenantService tenantService;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) return;

        String apiKey = ctx.getHeaderString(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            ctx.abortWith(Response.status(401)
                .entity("{\"error\":\"Missing X-Api-Key header\"}").build());
            return;
        }

        Tenant tenant = tenantService.findByApiKey(apiKey).orElse(null);
        if (tenant == null) {
            ctx.abortWith(Response.status(403)
                .entity("{\"error\":\"Invalid API key\"}").build());
            return;
        }

        if (!tenant.isActive()) {
            ctx.abortWith(Response.status(403)
                .entity("{\"error\":\"Tenant is inactive\"}").build());
            return;
        }

        ctx.setProperty("tenant", tenant);
        ctx.setProperty("businessCode", tenant.getBusinessCode());
    }
}
