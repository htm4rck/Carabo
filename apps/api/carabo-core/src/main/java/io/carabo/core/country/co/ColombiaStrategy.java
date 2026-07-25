package io.carabo.core.country.co;

import io.carabo.core.country.CountryStrategy;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Colombia (DIAN) electronic invoicing strategy.
 * UBL 2.1 DIAN, IVA 19%, NIT.
 * Status: STUB - pending full implementation.
 */
public class ColombiaStrategy implements CountryStrategy {

    @Override
    public String countryCode() { return "CO"; }

    @Override
    public List<String> validate(Document doc, Tenant tenant) {
        List<String> errors = new ArrayList<>();
        // NIT validation: 9-10 digits + verification digit
        if (doc.getIssuerRuc() == null || !doc.getIssuerRuc().matches("\\d{9,10}")) {
            errors.add("NIT del emisor debe tener 9-10 dígitos");
        }
        if (doc.getReceiverDoc() == null || doc.getReceiverDoc().isBlank()) {
            errors.add("Documento del receptor es obligatorio");
        }
        if (doc.getLines() == null || doc.getLines().isEmpty()) {
            errors.add("El documento debe tener al menos una línea");
        }
        return errors;
    }

    private final DianUblBuilder builder = new DianUblBuilder();

    @Override
    public String buildElectronicDocument(Document doc) {
        return builder.build(doc);
    }

    @Override
    public String taxAuthorityName() { return "DIAN"; }

    @Override
    public BigDecimal defaultTaxRate() { return new BigDecimal("0.19"); }

    @Override
    public String issuerDocLabel() { return "NIT"; }
}
