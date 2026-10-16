package io.carabo.app.security;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationPolicyTest {
    @Test
    void producerScopesAreLeastPrivilege() {
        Set<String> scopes = Set.of("DOCUMENT_INGEST", "DOCUMENT_READ");
        assertTrue(IntegrationPolicy.permits("/documents/ingest", "POST", scopes));
        assertTrue(IntegrationPolicy.permits("/documents/validate", "POST", scopes));
        assertTrue(IntegrationPolicy.permits("/documents/external/order-1", "GET", scopes));
        assertFalse(IntegrationPolicy.permits("/documents/emit", "POST", scopes));
        assertFalse(IntegrationPolicy.permits("/tenants", "GET", scopes));
        assertFalse(IntegrationPolicy.permits("/integrations", "GET", scopes));
    }
}
