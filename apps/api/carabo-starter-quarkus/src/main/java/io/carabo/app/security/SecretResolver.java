package io.carabo.app.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;


@ApplicationScoped
public class SecretResolver {
    @jakarta.inject.Inject ManagedCredentialStore managedCredentials;
    private static final String PREFIX = "secret://";

    @ConfigProperty(name = "carabo.secrets.base-path", defaultValue = "/run/secrets/carabo")
    String basePath;

    /** Resolves a mounted secret; plaintext values remain legacy-compatible. */
    public String resolve(String valueOrReference) {
        if (valueOrReference == null || !valueOrReference.startsWith(PREFIX)) return valueOrReference;

        if (valueOrReference.startsWith(ManagedCredentialStore.PREFIX)) {
            if (managedCredentials == null) throw new IllegalStateException("El almacén seguro de credenciales no está disponible");
            return managedCredentials.resolve(valueOrReference);
        }
        return SecretReferenceFiles.read(basePath, valueOrReference);
    }
}
