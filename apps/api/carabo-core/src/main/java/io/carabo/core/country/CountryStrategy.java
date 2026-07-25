package io.carabo.core.country;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;

import java.util.List;

/**
 * Strategy interface for country-specific electronic invoicing rules.
 * Each country (PE, CO, CL, MX) implements its own validation, XML format, and sending logic.
 */
public interface CountryStrategy {

    /** ISO 3166-1 alpha-2 country code */
    String countryCode();

    /** Validate document according to country-specific tax rules */
    List<String> validate(Document doc, Tenant tenant);

    /** Build XML/JSON in the country's required format (UBL 2.1 for PE, UBL 2.1 DIAN for CO, etc.) */
    String buildElectronicDocument(Document doc);

    /** Tax authority name for display */
    String taxAuthorityName();

    /** Default tax rate for the country */
    java.math.BigDecimal defaultTaxRate();

    /** Issuer document type label (RUC for PE, NIT for CO, RUT for CL, RFC for MX) */
    String issuerDocLabel();
}
