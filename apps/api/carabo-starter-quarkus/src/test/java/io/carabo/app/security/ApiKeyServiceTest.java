package io.carabo.app.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyServiceTest {
    private final ApiKeyService service = new ApiKeyService();

    @Test
    void generatesOpaqueKeysAndStoresStableFingerprints() {
        String first = service.generate();
        String second = service.generate();

        assertEquals(64, first.length());
        assertNotEquals(first, second);
        assertEquals(64, service.hash(first).length());
        assertEquals(service.hash(first), service.hash(first));
        assertNotEquals(first, service.hash(first));
    }
}
