package io.carabo.app.security;

import io.carabo.core.entity.Tenant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @Test
    void derivesBusinessCodeFromAuthenticatedTenant() {
        Tenant tenant = new Tenant();
        tenant.setBusinessCode("tenant-a");

        TenantContext context = new TenantContext();
        context.authenticateTenant(tenant);

        assertEquals("tenant-a", context.businessCode());
        assertFalse(context.isAdministrator());
    }

    @Test
    void administratorCannotBeUsedAsTenant() {
        TenantContext context = new TenantContext();
        context.authenticateAdministrator();

        assertTrue(context.isAdministrator());
        assertThrows(IllegalStateException.class, context::businessCode);
    }
}
