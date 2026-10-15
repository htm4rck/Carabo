package io.carabo.app.security;

import io.carabo.app.service.TenantService;
import io.carabo.core.entity.Tenant;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;

@Provider
public class ApiKeyFilter implements ContainerRequestFilter {
    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String ADMIN_KEY_HEADER = "X-Admin-Key";
    private static final String BUSINESS_HEADER = "business-code";
    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/version", "/metrics", "/openapi", "/q/openapi", "/swagger");

    @Inject TenantService tenantService;
    @Inject TenantContext tenantContext;
    @Inject HumanAccessService humanAccessService;
    @Inject SecurityIdentity securityIdentity;
    @Inject JsonWebToken jwt;
    @ConfigProperty(name = "carabo.security.admin-key") Optional<String> adminKey;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = "/" + ctx.getUriInfo().getPath().replaceFirst("^/", "");
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) return;

        String authorization = ctx.getHeaderString("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            authenticateHuman(ctx, path);
            return;
        }

        if (path.startsWith("/tenants")) {
            authenticateLegacyAdministrator(ctx);
            return;
        }
        authenticateIntegration(ctx);
    }

    private void authenticateHuman(ContainerRequestContext ctx, String path) {
        if (securityIdentity.isAnonymous() || jwt.getSubject() == null) {
            reject(ctx, 401, "Invalid or expired bearer token");
            return;
        }

        String businessCode = ctx.getHeaderString(BUSINESS_HEADER);
        if (businessCode == null || businessCode.isBlank()) {
            reject(ctx, 400, "Missing business-code header");
            return;
        }

        Object licenseType = jwt.getClaim("licenseType");
        boolean root = isRootLicense(licenseType);
        HumanAccessService.Access access = humanAccessService.resolve(jwt.getSubject(), businessCode, root);
        if (!RolePolicy.permits(path, ctx.getMethod(), access)) {
            reject(ctx, 403, "Your Carabo role does not permit this operation");
            return;
        }

        Tenant tenant = tenantService.findByBusinessCode(businessCode).orElse(null);
        if (tenant == null || !tenant.isActive()) {
            reject(ctx, 403, "The active business is not enabled in Carabo");
            return;
        }
        tenantContext.authenticateUser(tenant, jwt.getSubject(), access.roles(), access.root() || access.hasAny("ADMIN_Carabo"));
    }

    private void authenticateIntegration(ContainerRequestContext ctx) {
        String apiKey = ctx.getHeaderString(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            reject(ctx, 401, "Missing authentication credential");
            return;
        }
        Tenant tenant = tenantService.findByApiKey(apiKey).orElse(null);
        if (tenant == null) { reject(ctx, 403, "Invalid API key"); return; }
        if (!tenant.isActive()) { reject(ctx, 403, "Tenant is inactive"); return; }
        tenantContext.authenticateTenant(tenant);
    }

    private void authenticateLegacyAdministrator(ContainerRequestContext ctx) {
        String configuredKey = adminKey.filter(value -> !value.isBlank()).orElse(null);
        if (configuredKey == null) { reject(ctx, 503, "Tenant administration is not configured"); return; }
        String suppliedKey = ctx.getHeaderString(ADMIN_KEY_HEADER);
        if (suppliedKey == null || !MessageDigest.isEqual(configuredKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            reject(ctx, 403, "Invalid administrator credential");
            return;
        }
        tenantContext.authenticateAdministrator();
    }

    private void reject(ContainerRequestContext ctx, int status, String message) {
        ctx.abortWith(Response.status(status).type("application/json")
            .entity("{\"error\":\"" + message.replace("\"", "") + "\"}").build());
    }

    static boolean isRootLicense(Object licenseType) {
        return licenseType != null && "root".equalsIgnoreCase(licenseType.toString());
    }
}
