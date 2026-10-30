package io.carabo.app.service;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DispatchGuideReadinessTest {
    @Test void requiresBothOAuthCredentials() {
        var service = new DispatchGuideService();
        service.greClientId = Optional.empty();
        service.greClientSecret = Optional.empty();
        assertFalse(service.greCredentialsConfigured());
        service.greClientId = Optional.of("not-configured");
        service.greClientSecret = Optional.of("test-secret");
        assertFalse(service.greCredentialsConfigured());
        service.greClientId = Optional.of("test-client");
        assertTrue(service.greCredentialsConfigured());
    }
    @Test void addsIssuerOnlyToAnUnqualifiedSolUser() {
        assertEquals("20100066603MODDATOS", DispatchGuideService.greSolUser("20100066603", "MODDATOS"));
        assertEquals("20100066603MODDATOS", DispatchGuideService.greSolUser("20100066603", "20100066603MODDATOS"));
    }
}
