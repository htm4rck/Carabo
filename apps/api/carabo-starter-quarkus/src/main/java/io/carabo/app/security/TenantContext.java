package io.carabo.app.security;

import io.carabo.core.entity.Tenant;
import io.carabo.core.entity.ProducerIntegration;
import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;
import java.util.Set;

/**
 * Identity resolved by the authentication filter for the current HTTP request.
 * Business operations must use this context instead of tenant identifiers sent
 * by the caller.
 */
@RequestScoped
public class TenantContext {

    private Tenant tenant;
    private boolean administrator;
    private String userCode;
    private Set<String> roles = Set.of();
    private UUID producerId;
    private String producerCode;
    private Set<String> producerScopes = Set.of();

    public void authenticateTenant(Tenant tenant) {
        this.tenant = tenant;
        this.administrator = false;
    }

    public void authenticateProducer(Tenant tenant, ProducerIntegration producer, Set<String> scopes) {
        this.tenant = tenant;
        this.administrator = false;
        this.producerId = producer.getId();
        this.producerCode = producer.getCode();
        this.producerScopes = Set.copyOf(scopes);
    }

    public void authenticateAdministrator() {
        this.tenant = null;
        this.administrator = true;
    }

    public void authenticateUser(Tenant tenant, String userCode, Set<String> roles, boolean administrator) {
        this.tenant = tenant;
        this.userCode = userCode;
        this.roles = Set.copyOf(roles);
        this.administrator = administrator;
    }

    public Tenant tenant() {
        if (tenant == null) {
            throw new IllegalStateException("No tenant authenticated for this request");
        }
        return tenant;
    }

    public String businessCode() {
        return tenant().getBusinessCode();
    }

    public boolean canManage(Tenant target) {
        return administrator && (isPlatformAdministrator()
            || tenant != null && tenant.getBusinessCode().equals(target.getBusinessCode()));
    }

    public boolean isPlatformAdministrator() {
        return administrator && (userCode == null || roles.contains("ROOT_PLATFORM"));
    }

    public boolean isAdministrator() {
        return administrator;
    }

    public String userCode() { return userCode; }
    public Set<String> roles() { return roles; }
    public UUID producerId() { return producerId; }
    public String producerCode() { return producerCode; }
    public Set<String> producerScopes() { return producerScopes; }
}
