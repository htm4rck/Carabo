package io.carabo.app.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyFilterTest {
    @Test
    void recognizesRootLicenseFromJwtStringClaim() {
        assertTrue(ApiKeyFilter.isRootLicense("root"));
        assertTrue(ApiKeyFilter.isRootLicense("ROOT"));
    }

    @Test
    void rejectsMissingOrNonRootLicenseClaims() {
        assertFalse(ApiKeyFilter.isRootLicense(null));
        assertFalse(ApiKeyFilter.isRootLicense("professional"));
    }
}
