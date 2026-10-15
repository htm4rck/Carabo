package io.carabo.app.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecretResolverTest {
    @TempDir Path secrets;

    @Test
    void resolvesMountedSecretsAndRejectsTraversal() throws Exception {
        Files.writeString(secrets.resolve("tenant-sol-password"), "s3cret\n");
        SecretResolver resolver = new SecretResolver();
        resolver.basePath = secrets.toString();

        assertEquals("s3cret", resolver.resolve("secret://tenant-sol-password"));
        assertEquals("legacy", resolver.resolve("legacy"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("secret://../outside"));
    }
}
