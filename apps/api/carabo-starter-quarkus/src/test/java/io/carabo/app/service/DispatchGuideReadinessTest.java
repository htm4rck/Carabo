package io.carabo.app.service;

import io.carabo.core.entity.Tenant;
import io.carabo.app.security.SecretResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DispatchGuideReadinessTest {
    private DispatchGuideService service() {
        var service = new DispatchGuideService();
        service.greClientId = Optional.empty(); service.greClientSecret = Optional.empty();
        service.secretResolver = new SecretResolver();
        return service;
    }
    @Test void requiresBothOAuthCredentials() {
        var service = service(); var tenant = new Tenant();
        assertFalse(service.greCredentialsConfigured(tenant));
        service.greClientId = Optional.of("not-configured"); service.greClientSecret = Optional.of("test-secret");
        assertFalse(service.greCredentialsConfigured(tenant));
        service.greClientId = Optional.of("test-client");
        assertTrue(service.greCredentialsConfigured(tenant));
    }
    @Test void tenantCredentialsNeverMixWithLegacyDefaultsOrOtherCompanies() {
        var service = service(); service.greClientId = Optional.of("legacy"); service.greClientSecret = Optional.of("legacy-secret");
        var tenant = new Tenant(); tenant.setGreClientId("own-client");
        assertFalse(service.greCredentialsConfigured(tenant));
        tenant.setGreClientSecret("own-secret"); assertTrue(service.greCredentialsConfigured(tenant));
        var second = new Tenant(); second.setGreClientSecret("second-secret");
        assertFalse(service.greCredentialsConfigured(second));
    }
    @Test void addsIssuerOnlyToAnUnqualifiedSolUser() {
        assertEquals("20100066603MODDATOS", DispatchGuideService.greSolUser("20100066603", "MODDATOS"));
        assertEquals("20100066603MODDATOS", DispatchGuideService.greSolUser("20100066603", "20100066603MODDATOS"));
    }
}
