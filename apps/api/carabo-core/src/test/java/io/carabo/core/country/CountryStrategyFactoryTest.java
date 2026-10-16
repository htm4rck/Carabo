package io.carabo.core.country;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CountryStrategyFactoryTest {
    @Test
    void exposesOnlyHomologatedCountries() {
        assertTrue(CountryStrategyFactory.isSupported("PE"));
        assertFalse(CountryStrategyFactory.isSupported("CO"));
        assertFalse(CountryStrategyFactory.isSupported("CL"));
        assertFalse(CountryStrategyFactory.isSupported("MX"));
        assertThrows(UnsupportedOperationException.class, () -> CountryStrategyFactory.forCountry("CO"));
    }
}
