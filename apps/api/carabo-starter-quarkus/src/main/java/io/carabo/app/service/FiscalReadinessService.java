package io.carabo.app.service;

import io.carabo.app.security.SecretResolver;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.SendMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Builds a safe, non-secret preflight for direct SUNAT emission. */
@ApplicationScoped
public class FiscalReadinessService {

    @Inject
    SecretResolver secretResolver;

    @ConfigProperty(name = "carabo.certs.base-path", defaultValue = "/app/certs")
    String certsBasePath;

    public FiscalReadiness assess(Tenant tenant) {
        List<ReadinessCheck> checks = new ArrayList<>();
        checks.add(check("tenant-active", "Empresa activa", tenant.isActive(), true,
            "Activa la empresa antes de emitir."));
        checks.add(check("sunat-direct", "Envío directo a SUNAT", tenant.getSendMode() == SendMode.SUNAT_DIRECT, true,
            "Selecciona SUNAT directo como canal de envío."));
        checks.add(check("issuer", "Identidad fiscal", present(tenant.getIssuerRuc())
                && tenant.getIssuerRuc().matches("\\d{11}") && present(tenant.getIssuerName()), true,
            "Completa RUC y razón social del emisor."));
        checks.add(check("series", "Series documentales", present(tenant.getInvoiceSeries())
                && present(tenant.getReceiptSeries()), true,
            "Configura las series de factura y boleta."));
        checks.add(check("api-key", "Clave de integración", present(tenant.getApiKeyHash()), true,
            "Rota la API key y consérvala en el secret store del productor."));
        checks.add(check("sol-user", "Usuario SOL", present(tenant.getSolUser()), true,
            "Configura el usuario SOL del emisor."));
        checks.add(check("sol-password", "Contraseña SOL", resolves(tenant.getSolPassword()), true,
            "Guarda la contraseña en el secret store y configura su referencia secret://."));

        boolean certificateMetadata = present(tenant.getCertificateFile())
            && present(tenant.getCertificateAlias())
            && tenant.getCertificateValidTo() != null;
        checks.add(check("certificate-metadata", "Identidad de firma", certificateMetadata, true,
            "Carga un certificado PKCS#12 válido desde Configuración."));
        checks.add(check("certificate-file", "Certificado montado", certificateFileExists(tenant), true,
            "Vuelve a cargar el certificado en el volumen persistente."));
        checks.add(check("certificate-password", "Contraseña del certificado",
            resolves(tenant.getCertificatePassword()), true,
            "Guarda la contraseña en el secret store y carga el certificado con esa referencia."));
        checks.add(check("certificate-validity", "Vigencia del certificado", certificateIsCurrent(tenant), true,
            "Carga un certificado actualmente vigente."));
        checks.add(check("sunat-endpoint", "Endpoint SUNAT seguro", endpointIsSafe(tenant), true,
            "Usa el endpoint HTTPS de SUNAT beta/homologación para la prueba."));
        checks.add(check("webhook", "Webhook de resultado", present(tenant.getWebhookUrl()), false,
            "Opcional: configura un webhook; GET por externalId seguirá siendo la fuente autoritativa."));

        boolean ready = checks.stream().filter(ReadinessCheck::required).allMatch(ReadinessCheck::ready);
        long blockers = checks.stream().filter(ReadinessCheck::required).filter(item -> !item.ready()).count();
        return new FiscalReadiness(tenant.getBusinessCode(), tenant.getEnvironment(),
            tenant.getSendMode() != null ? tenant.getSendMode().name() : null,
            ready, blockers, checks);
    }

    private ReadinessCheck check(String code, String label, boolean ready, boolean required, String action) {
        return new ReadinessCheck(code, label, ready, required, ready ? "Configurado" : action);
    }

    private boolean certificateFileExists(Tenant tenant) {
        if (!present(tenant.getCertificateFile())) return false;
        Path root = Path.of(certsBasePath).toAbsolutePath().normalize();
        Path certificate = root.resolve(tenant.getCertificateFile()).normalize();
        return certificate.startsWith(root) && Files.isRegularFile(certificate) && Files.isReadable(certificate);
    }

    private boolean certificateIsCurrent(Tenant tenant) {
        LocalDate today = LocalDate.now();
        return tenant.getCertificateValidFrom() != null && tenant.getCertificateValidTo() != null
            && !tenant.getCertificateValidFrom().isAfter(today)
            && !tenant.getCertificateValidTo().isBefore(today);
    }

    private boolean endpointIsSafe(Tenant tenant) {
        String endpoint = tenant.getSunatEndpoint();
        if (!present(endpoint)) return true; // DocumentService selects the official endpoint for the environment.
        try {
            URI uri = URI.create(endpoint);
            return "https".equalsIgnoreCase(uri.getScheme()) && present(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean resolves(String value) {
        if (!present(value)) return false;
        try {
            String resolved = secretResolver.resolve(value);
            return present(resolved);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public record FiscalReadiness(String businessCode, String environment, String sendMode,
                                  boolean ready, long blockers, List<ReadinessCheck> checks) {}

    public record ReadinessCheck(String code, String label, boolean ready, boolean required, String detail) {}
}
