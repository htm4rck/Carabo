package io.carabo.core.validation;

import java.math.BigDecimal;
import java.util.Map;

/** Supported SUNAT Catalog 07 affectations for the current MVP. */
public final class PeruTaxRules {
    private static final Map<String, TaxProfile> PROFILES = Map.of(
        "10", new TaxProfile("10", new BigDecimal("18.00"), "1000", "IGV", "VAT", "S"),
        "20", new TaxProfile("20", BigDecimal.ZERO, "9997", "EXO", "VAT", "E"),
        "30", new TaxProfile("30", BigDecimal.ZERO, "9998", "INA", "FRE", "O"),
        "40", new TaxProfile("40", BigDecimal.ZERO, "9995", "EXP", "FRE", "G")
    );

    private PeruTaxRules() {}

    public static TaxProfile profile(String affectationCode) {
        return PROFILES.get(affectationCode);
    }

    public static TaxProfile profileByTaxCode(String taxCode) {
        return PROFILES.values().stream()
            .filter(profile -> profile.taxCode().equals(taxCode))
            .findFirst().orElse(null);
    }

    public record TaxProfile(String affectationCode, BigDecimal rate, String taxCode,
                             String taxName, String taxTypeCode, String categoryCode) {}
}
