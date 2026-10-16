package io.carabo.app.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantResourceValidationTest {
    @Test
    void acceptsValidFiscalConfiguration() {
        var request = new TenantResource.UpdateTenantRequest();
        request.issuerRuc = "20123456789";
        request.issuerUbigeo = "150101";
        request.sendMode = "SUNAT_DIRECT";
        request.environment = "HOMOLOGATION";
        request.maxDocumentsPerDay = 2500;

        assertNull(TenantResource.validateUpdate(request));
    }

    @Test
    void rejectsInvalidRucAndOperationalLimits() {
        var invalidRuc = new TenantResource.UpdateTenantRequest();
        invalidRuc.issuerRuc = "123";
        assertEquals("El RUC debe contener 11 dígitos", TenantResource.validateUpdate(invalidRuc));

        var invalidLimit = new TenantResource.UpdateTenantRequest();
        invalidLimit.maxDocumentsPerDay = 0;
        assertEquals("El máximo diario debe estar entre 1 y 100000", TenantResource.validateUpdate(invalidLimit));
    }

    @Test
    void requiresSecretReferencesForSensitiveUpdates() {
        var plaintextSol = new TenantResource.UpdateTenantRequest();
        plaintextSol.solPassword = "plaintext";
        assertEquals("La contraseña SOL debe usar una referencia secret://",
            TenantResource.validateUpdate(plaintextSol));

        var references = new TenantResource.UpdateTenantRequest();
        references.solPassword = "secret://tenant-sol-password";
        references.certificatePassword = "secret://tenant-certificate-password";
        references.oseApiToken = "secret://tenant-ose-token";
        assertNull(TenantResource.validateUpdate(references));
    }

    @Test
    void blocksUnhomologatedCountriesAndIncompleteOse() {
        var country = new TenantResource.UpdateTenantRequest();
        country.countryCode = "CO";
        assertEquals("Solo PE está habilitado; otros países requieren compliance y homologación antes de activarse",
            TenantResource.validateUpdate(country));

        var ose = new TenantResource.UpdateTenantRequest();
        ose.countryCode = "PE";
        ose.sendMode = "OSE";
        ose.oseBaseUrl = "https://ose.example.test/gateway";
        ose.oseApiToken = "secret://tenant-ose-token";
        assertNull(TenantResource.validateUpdate(ose));
    }
}
