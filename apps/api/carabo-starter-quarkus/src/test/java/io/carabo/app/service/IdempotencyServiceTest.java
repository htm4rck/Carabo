package io.carabo.app.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdempotencyServiceTest {

    @Test
    void normalizesKeysBeforeLockingAndPersisting() {
        assertNull(IdempotencyService.normalize(null));
        assertNull(IdempotencyService.normalize("   "));
        assertEquals("sale-1042", IdempotencyService.normalize("  sale-1042  "));
    }
}
