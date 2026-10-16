package io.carabo.app.security;

import io.carabo.core.entity.Tenant;
import io.carabo.core.entity.ProducerIntegration;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

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

    @Test
    void carriesIndependentProducerIdentity() {
        Tenant tenant = new Tenant();
        tenant.setBusinessCode("tenant-a");
        ProducerIntegration producer = new ProducerIntegration();
        producer.setBusinessCode("tenant-a");
        producer.setCode("erp-main");
        producer.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        TenantContext context = new TenantContext();
        context.authenticateProducer(tenant, producer, Set.of("DOCUMENT_INGEST"));

        assertEquals("erp-main", context.producerCode());
        assertEquals(Set.of("DOCUMENT_INGEST"), context.producerScopes());
    }
}
