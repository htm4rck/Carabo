package io.carabo.app.service;

import io.carabo.app.security.SecretResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class CertificateService {
    static final long MAX_CERTIFICATE_BYTES = 5L * 1024 * 1024;

    @Inject
    SecretResolver secretResolver;

    @ConfigProperty(name = "carabo.certs.base-path", defaultValue = "/app/certs")
    String certsBasePath;

    public CertificateMetadata validateAndStore(UUID tenantId, String originalFileName, Path upload,
                                                long size, String passwordReference) {
        validateRequest(originalFileName,upload,size,passwordReference);
        return validateAndStoreWithPassword(tenantId,originalFileName,upload,size,passwordReference,secretResolver.resolve(passwordReference));
    }

    public CertificateMetadata validateAndStoreWithPassword(UUID tenantId,String originalFileName,Path upload,long size,String passwordReference,String password) {
        validateRequest(originalFileName, upload, size, passwordReference);
        SigningMaterial signingMaterial = readSigningCertificate(upload, password);
        X509Certificate certificate = signingMaterial.certificate();

        try {
            certificate.checkValidity();
        } catch (Exception e) {
            throw new IllegalArgumentException("El certificado no está vigente", e);
        }

        Path root = Path.of(certsBasePath).toAbsolutePath().normalize();
        String storedFileName = tenantId + "-" + UUID.randomUUID() + ".p12";
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Ruta de certificado inválida");

        try {
            Files.createDirectories(root);
            Path staged = Files.createTempFile(root, tenantId + "-", ".upload");
            try {
                Files.copy(upload, staged, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el certificado", e);
        }

        LocalDate validFrom = certificate.getNotBefore().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate validTo = certificate.getNotAfter().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        return new CertificateMetadata(storedFileName, passwordReference, signingMaterial.alias(), validFrom, validTo);
    }

    public void verifyPassword(String fileName, String password) {
        Path root=Path.of(certsBasePath).toAbsolutePath().normalize(),file=root.resolve(fileName).normalize();
        if(!file.startsWith(root))throw new IllegalArgumentException("Certificado inválido");
        readSigningCertificate(file,password);
    }

    private void validateRequest(String originalFileName, Path upload, long size, String passwordReference) {
        if (upload == null || originalFileName == null || originalFileName.isBlank() || size <= 0) {
            throw new IllegalArgumentException("Selecciona un certificado PKCS#12");
        }
        String lowerName = originalFileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".p12") && !lowerName.endsWith(".pfx")) {
            throw new IllegalArgumentException("El certificado debe tener extensión .p12 o .pfx");
        }
        if (size > MAX_CERTIFICATE_BYTES) {
            throw new IllegalArgumentException("El certificado supera el límite de 5 MB");
        }
        if (passwordReference == null || !passwordReference.startsWith("secret://")) {
            throw new IllegalArgumentException("Usa una referencia secret:// para la contraseña");
        }
    }

    private SigningMaterial readSigningCertificate(Path upload, String password) {
        try (InputStream input = Files.newInputStream(upload)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] passwordChars = password.toCharArray();
            keyStore.load(input, passwordChars);
            Enumeration<String> aliases = keyStore.aliases();
            var candidates = new ArrayList<SigningMaterial>();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias) && keyStore.getKey(alias, passwordChars) != null
                    && keyStore.getCertificate(alias) instanceof X509Certificate certificate) {
                    candidates.add(new SigningMaterial(alias, certificate));
                }
            }
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("El PKCS#12 no contiene una clave privada y certificado válidos");
            }
            if (candidates.size() > 1) {
                throw new IllegalArgumentException("El PKCS#12 contiene múltiples claves privadas; carga un archivo con una sola identidad de firma");
            }
            return candidates.get(0);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo abrir el certificado; revisa el archivo y su secreto", e);
        }
    }

    private record SigningMaterial(String alias, X509Certificate certificate) {}

    public record CertificateMetadata(String fileName, String passwordReference, String alias,
                                      LocalDate validFrom, LocalDate validTo) {}
}
