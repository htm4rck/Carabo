package io.carabo.app.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class SecretResolver {
    private static final String PREFIX = "secret://";

    @ConfigProperty(name = "carabo.secrets.base-path", defaultValue = "/run/secrets/carabo")
    String basePath;

    /** Resolves a mounted secret; plaintext values remain legacy-compatible. */
    public String resolve(String valueOrReference) {
        if (valueOrReference == null || !valueOrReference.startsWith(PREFIX)) return valueOrReference;

        String name = valueOrReference.substring(PREFIX.length());
        if (name.isBlank()) throw new IllegalArgumentException("Secret reference is empty");

        Path root = Path.of(basePath).toAbsolutePath().normalize();
        Path secret = root.resolve(name).normalize();
        if (!secret.startsWith(root)) throw new IllegalArgumentException("Secret reference escapes configured store");

        try {
            return Files.readString(secret, StandardCharsets.UTF_8).stripTrailing();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read secret reference " + valueOrReference, e);
        }
    }
}
