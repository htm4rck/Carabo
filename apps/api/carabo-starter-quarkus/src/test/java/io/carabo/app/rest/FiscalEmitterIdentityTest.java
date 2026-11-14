package io.carabo.app.rest;

import io.carabo.app.security.TenantContext;
import io.carabo.core.entity.Tenant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FiscalEmitterIdentityTest {
    @Test
    void identityUsesTheAuthenticatedFiscalTenantAndContainsNoCredential() {
        var tenant = new Tenant();
        tenant.setBusinessCode("B20123456789");
        tenant.setIssuerRuc("20123456789");
        tenant.setCountryCode("PE");
        tenant.setEnvironment("BETA");
        tenant.setSolPassword("must-not-leave-identity");
        var context = new TenantContext();
        context.authenticateUser(tenant, "USER", Set.of(), false);
        var resource = new DocumentResource();
        resource.tenantContext = context;
        var response = resource.identity();
        assertEquals(200, response.getStatus());
        var identity = (DocumentResource.FiscalEmitterIdentity) response.getEntity();
        assertEquals("B20123456789", identity.businessCode());
        assertEquals("20123456789", identity.issuerRuc());
        assertEquals("PE", identity.countryCode());
        assertEquals("BETA", identity.environment());
        assertEquals(Set.of("businessCode", "issuerRuc", "countryCode", "environment"),
            Arrays.stream(identity.getClass().getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet()));
        assertFalse(identity.toString().contains("must-not-leave-identity"));
    }
    @Test
    void anUnconfiguredIssuerStillPreservesTheExistingBusinessIdentityContract() {
        var tenant = new Tenant();
        tenant.setBusinessCode("B20123456789");
        var context = new TenantContext();
        context.authenticateUser(tenant, "USER", Set.of(), false);
        var resource = new DocumentResource();resource.tenantContext = context;
        var identity = (DocumentResource.FiscalEmitterIdentity) resource.identity().getEntity();
        assertEquals("B20123456789", identity.businessCode());
        assertNull(identity.issuerRuc());
    }
}
