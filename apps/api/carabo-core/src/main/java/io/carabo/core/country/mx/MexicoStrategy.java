package io.carabo.core.country.mx;

import io.carabo.core.country.CountryStrategy;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Mexico (SAT) electronic invoicing strategy.
 * CFDI 4.0 XML, IVA 16%, RFC.
 * Status: STUB - pending full implementation.
 */
public class MexicoStrategy implements CountryStrategy {

    @Override
    public String countryCode() { return "MX"; }

    @Override
    public List<String> validate(Document doc, Tenant tenant) {
        List<String> errors = new ArrayList<>();
        // RFC validation: 12 chars (moral) or 13 chars (física)
        if (doc.getIssuerRuc() == null || !doc.getIssuerRuc().matches("[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}")) {
            errors.add("RFC del emisor no tiene formato válido");
        }
        if (doc.getReceiverDoc() == null || doc.getReceiverDoc().isBlank()) {
            errors.add("RFC del receptor es obligatorio");
        }
        if (doc.getLines() == null || doc.getLines().isEmpty()) {
            errors.add("El documento debe tener al menos una línea");
        }
        return errors;
    }

    private final CfdiBuilder builder = new CfdiBuilder();

    @Override
    public String buildElectronicDocument(Document doc) {
        return builder.build(doc);
    }

    @Override
    public String taxAuthorityName() { return "SAT"; }

    @Override
    public BigDecimal defaultTaxRate() { return new BigDecimal("0.16"); }

    @Override
    public String issuerDocLabel() { return "RFC"; }
}
