package io.carabo.app.security;

import io.carabo.app.service.TenantService;
import io.carabo.core.entity.Tenant;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;

@Provider
public class ApiKeyFilter implements ContainerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/health", "/metrics", "/openapi", "/q/openapi", "/swagger");

    @Inject
    TenantService tenantService;

    @Inject
    TenantContext tenantContext;

    @ConfigProperty(name = "carabo.security.admin-key")
    Optional<String> adminKey;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = "/" + ctx.getUriInfo().getPath().replaceFirst("^/", "");
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) return;

        if (path.startsWith("/tenants")) {
            authenticateAdministrator(ctx);
            return;
        }

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

        tenantContext.authenticateTenant(tenant);
    }

    private void authenticateAdministrator(ContainerRequestContext ctx) {
        String configuredKey = adminKey.filter(value -> !value.isBlank()).orElse(null);
        if (configuredKey == null) {
            ctx.abortWith(Response.status(503)
                .entity("{\"error\":\"Tenant administration is not configured\"}").build());
            return;
        }

        String suppliedKey = ctx.getHeaderString(ADMIN_KEY_HEADER);
        if (suppliedKey == null || !MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            ctx.abortWith(Response.status(403)
                .entity("{\"error\":\"Invalid administrator credential\"}").build());
            return;
        }

        tenantContext.authenticateAdministrator();
    }
}
