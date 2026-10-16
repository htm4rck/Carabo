package io.carabo.app.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CertificateServiceTest {
    private final CertificateService service = new CertificateService();

    @Test
    void rejectsUnsupportedFilesBeforeReadingThem() {
        var error = assertThrows(IllegalArgumentException.class, () ->
            service.validateAndStore(UUID.randomUUID(), "certificate.pem", Path.of("missing"), 20, "secret://cert-password"));
        assertEquals("El certificado debe tener extensión .p12 o .pfx", error.getMessage());
    }

    @Test
    void requiresSecretReferenceInsteadOfPlaintextPassword() {
        var error = assertThrows(IllegalArgumentException.class, () ->
            service.validateAndStore(UUID.randomUUID(), "certificate.p12", Path.of("missing"), 20, "plaintext"));
        assertEquals("Usa una referencia secret:// para la contraseña", error.getMessage());
    }

    @Test
    void rejectsOversizedCertificate() {
        var error = assertThrows(IllegalArgumentException.class, () ->
            service.validateAndStore(UUID.randomUUID(), "certificate.pfx", Path.of("missing"),
                CertificateService.MAX_CERTIFICATE_BYTES + 1, "secret://cert-password"));
        assertEquals("El certificado supera el límite de 5 MB", error.getMessage());
    }
}
