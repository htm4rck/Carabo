package io.carabo.app.service;

import io.carabo.app.security.SecretResolver;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.SendMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiscalReadinessServiceTest {

    @TempDir
    Path certificates;

    @Test
    void reportsRequiredBlockersWithoutExposingSecretValues() {
        FiscalReadinessService service = service();
        Tenant tenant = baseTenant();

        var result = service.assess(tenant);

        assertFalse(result.ready());
        assertEquals(6, result.blockers());
        assertTrue(result.checks().stream().noneMatch(item -> item.detail().contains("super-secret")));
    }

    @Test
    void webhookIsOptionalForAuthoritativePollingFlow() throws Exception {
        FiscalReadinessService service = service();
        Tenant tenant = baseTenant();
        tenant.setSolUser("20123456789MODDATOS");
        tenant.setSolPassword("secret://sol-password");
        tenant.setCertificateFile("tenant.p12");
        tenant.setCertificatePassword("secret://certificate-password");
        tenant.setCertificateAlias("signing-key");
        tenant.setCertificateValidFrom(LocalDate.now().minusDays(1));
        tenant.setCertificateValidTo(LocalDate.now().plusYears(1));
        Files.writeString(certificates.resolve("tenant.p12"), "not-read-by-preflight");

        var result = service.assess(tenant);

        assertTrue(result.ready());
        assertEquals(0, result.blockers());
        assertTrue(result.checks().stream().anyMatch(item -> item.code().equals("webhook") && !item.required()));
    }

    private FiscalReadinessService service() {
        FiscalReadinessService service = new FiscalReadinessService();
        service.certsBasePath = certificates.toString();
        service.fiscalClock = new FiscalClock("America/Lima");
        service.secretResolver = new SecretResolver() {
            @Override
            public String resolve(String valueOrReference) {
                return valueOrReference != null && valueOrReference.startsWith("secret://")
                    ? "super-secret" : valueOrReference;
            }
        };
        return service;
    }

    private Tenant baseTenant() {
        Tenant tenant = new Tenant();
        tenant.setBusinessCode("B20999999990");
        tenant.setIssuerRuc("20123456789");
        tenant.setIssuerName("Empresa E2E SAC");
        tenant.setSendMode(SendMode.SUNAT_DIRECT);
        tenant.setEnvironment("BETA");
        tenant.setInvoiceSeries("F001");
        tenant.setReceiptSeries("B001");
        tenant.setApiKeyHash("a".repeat(64));
        tenant.setActive(true);
        return tenant;
    }
}
