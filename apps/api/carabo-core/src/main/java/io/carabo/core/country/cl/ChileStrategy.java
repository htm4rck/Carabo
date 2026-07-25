package io.carabo.core.country.cl;

import io.carabo.core.country.CountryStrategy;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
/**
 * Chile (SII) electronic invoicing strategy.
 * DTE XML format, IVA 19%, RUT.
 * Status: STUB - pending full implementation.
 */
public class ChileStrategy implements CountryStrategy {

    @Override
    public String countryCode() { return "CL"; }

    @Override
    public List<String> validate(Document doc, Tenant tenant) {
        List<String> errors = new ArrayList<>();
        // RUT validation: format XX.XXX.XXX-X
        if (doc.getIssuerRuc() == null || doc.getIssuerRuc().isBlank()) {
            errors.add("RUT del emisor es obligatorio");
        }
        if (doc.getReceiverDoc() == null || doc.getReceiverDoc().isBlank()) {
            errors.add("RUT del receptor es obligatorio");
        }
        if (doc.getLines() == null || doc.getLines().isEmpty()) {
            errors.add("El documento debe tener al menos una línea");
        }
        return errors;
    }

    private final SiiDteBuilder builder = new SiiDteBuilder();

    @Override
    public String buildElectronicDocument(Document doc) {
        return builder.build(doc);
    }

    @Override
    public String taxAuthorityName() { return "SII"; }

    @Override
    public BigDecimal defaultTaxRate() { return new BigDecimal("0.19"); }

    @Override
    public String issuerDocLabel() { return "RUT"; }
}
