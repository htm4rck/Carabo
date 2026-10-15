package io.carabo.app.rest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TenantResponseTest {

    @Test
    void regularResponsesDoNotExposeCredentials() {
        Set<String> fields = Arrays.stream(TenantResource.TenantResponse.class.getFields())
            .map(java.lang.reflect.Field::getName)
            .collect(Collectors.toSet());

        assertFalse(fields.contains("apiKey"));
        assertFalse(fields.contains("solUser"));
        assertFalse(fields.contains("solPassword"));
        assertFalse(fields.contains("oseApiToken"));
        assertFalse(fields.contains("certificatePassword"));
        assertFalse(fields.contains("webhookSecret"));
    }
}
