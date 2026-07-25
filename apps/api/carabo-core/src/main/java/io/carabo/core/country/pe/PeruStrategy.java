package io.carabo.core.country.pe;

import io.carabo.core.country.CountryStrategy;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.ubl.UblBuilder;
import io.carabo.core.validation.DocumentValidator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Peru (SUNAT) electronic invoicing strategy.
 * UBL 2.1, IGV 18%, RUC 11 digits.
 */
public class PeruStrategy implements CountryStrategy {

    private final DocumentValidator validator = new DocumentValidator();
    private final UblBuilder ublBuilder = new UblBuilder();

    @Override
    public String countryCode() { return "PE"; }

    @Override
    public List<String> validate(Document doc, Tenant tenant) {
        return validator.validate(doc, tenant);
    }

    @Override
    public String buildElectronicDocument(Document doc) {
        return ublBuilder.build(doc);
    }

    @Override
    public String taxAuthorityName() { return "SUNAT"; }

    @Override
    public BigDecimal defaultTaxRate() { return new BigDecimal("0.18"); }

    @Override
    public String issuerDocLabel() { return "RUC"; }
}
