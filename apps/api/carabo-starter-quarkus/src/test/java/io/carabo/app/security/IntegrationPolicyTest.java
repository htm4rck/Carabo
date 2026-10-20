package io.carabo.app.security;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationPolicyTest {
    @Test
    void fiscalRecoveryRequiresExplicitEmitScope() {
        String document = "/documents/513fa2c8-9c9a-4d15-ae9c-c9ea93797733";
        assertFalse(IntegrationPolicy.permits(document + "/retry", "POST", Set.of("DOCUMENT_READ")));
        assertFalse(IntegrationPolicy.permits(document + "/retry", "POST", Set.of("DOCUMENT_INGEST")));
        assertTrue(IntegrationPolicy.permits(document + "/retry", "POST", Set.of("DOCUMENT_EMIT")));
        assertTrue(IntegrationPolicy.permits(document + "/status", "POST", Set.of("DOCUMENT_READ")));
        assertFalse(IntegrationPolicy.permits(document + "/status", "POST", Set.of("DOCUMENT_EMIT")));
        assertTrue(IntegrationPolicy.permits("/documents/identity", "GET", Set.of("DOCUMENT_READ")));
        assertFalse(IntegrationPolicy.permits("/documents/identity", "GET", Set.of("DOCUMENT_EMIT")));
        assertFalse(IntegrationPolicy.permits("/documents/batch/void", "POST", Set.of("DOCUMENT_READ")));
        assertTrue(IntegrationPolicy.permits("/documents/batch/void", "POST", Set.of("DOCUMENT_EMIT")));
    }

    @Test
    void producerScopesAreLeastPrivilege() {
        Set<String> scopes = Set.of("DOCUMENT_INGEST", "DOCUMENT_READ");
        assertTrue(IntegrationPolicy.permits("/documents/ingest", "POST", scopes));
        assertTrue(IntegrationPolicy.permits("/documents/validate", "POST", scopes));
        assertTrue(IntegrationPolicy.permits("/documents/external/order-1", "GET", scopes));
        assertFalse(IntegrationPolicy.permits("/documents/emit", "POST", scopes));
        assertFalse(IntegrationPolicy.permits("/dispatch-guides/emit", "POST", scopes));
        assertTrue(IntegrationPolicy.permits("/dispatch-guides/validate", "POST", scopes));
        assertTrue(IntegrationPolicy.permits("/dispatch-guides", "GET", scopes));
        assertTrue(IntegrationPolicy.permits("/dispatch-guides/513fa2c8-9c9a-4d15-ae9c-c9ea93797733/xml", "GET", scopes));
        assertTrue(IntegrationPolicy.permits("/dispatch-guides/513fa2c8-9c9a-4d15-ae9c-c9ea93797733/status", "POST", scopes));
        assertTrue(IntegrationPolicy.permits("/dispatch-guides/emit", "POST", Set.of("DOCUMENT_EMIT")));
        assertFalse(IntegrationPolicy.permits("/dispatch-guides/validate", "POST", Set.of("DOCUMENT_EMIT")));
        assertFalse(IntegrationPolicy.permits("/tenants", "GET", scopes));
        assertFalse(IntegrationPolicy.permits("/integrations", "GET", scopes));
    }
}
