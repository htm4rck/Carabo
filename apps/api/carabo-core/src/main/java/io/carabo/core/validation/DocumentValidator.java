package io.carabo.core.validation;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a Document against tax authority rules before XML generation.
 * Returns a list of errors. Empty list = valid.
 */
public class DocumentValidator {

    private static final ZoneId PERU_ZONE = ZoneId.of("America/Lima");

    private static final java.util.Set<String> CREDIT_NOTE_REASON_CODES = java.util.Set.of(
        "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13");
    private static final java.util.Set<String> DEBIT_NOTE_REASON_CODES = java.util.Set.of(
        "01", "02", "03", "11", "12");

    public List<String> validate(Document doc, Tenant tenant) {
        List<String> errors = new ArrayList<>();
        errors.addAll(validateStructure(doc));
        errors.addAll(validateFiscalReadiness(tenant));
        return errors;
    }

    /** Contract/document validation. It must not depend on external fiscal setup. */
    public List<String> validateStructure(Document doc) {
        List<String> errors = new ArrayList<>();
        validateHeader(doc, errors);
        validateIssuer(doc, errors);
        validateReceiver(doc, errors);
        validateLines(doc, errors);
        validateTotals(doc, errors);
        validateReference(doc, errors);
        validateDateRange(doc, errors);

        return errors;
    }

    /** Preconditions that block issuance but must not discard an accepted intent. */
    public List<String> validateFiscalReadiness(Tenant tenant) {
        List<String> errors = new ArrayList<>();
        validateTenant(tenant, errors);
        return errors;
    }

    private void validateTenant(Tenant tenant, List<String> errors) {
        if (!tenant.isActive()) {
            errors.add("Tenant desactivado: " + tenant.getBusinessCode());
        }
        if (tenant.getCertificateFile() == null || tenant.getCertificateFile().isBlank()) {
            errors.add("Certificado digital no configurado");
        }
        if (tenant.getCertificateValidFrom() != null && tenant.getCertificateValidFrom().isAfter(today())) {
            errors.add("Certificado digital aún no vigente (válido desde " + tenant.getCertificateValidFrom() + ")");
        }
        if (tenant.getCertificateValidTo() != null && tenant.getCertificateValidTo().isBefore(today())) {
            errors.add("Certificado digital vencido (válido hasta " + tenant.getCertificateValidTo() + ")");
        }
    }

    private void validateHeader(Document doc, List<String> errors) {
        if (doc.getDocType() == null) {
            errors.add("Tipo de documento requerido");
        }
        if (doc.getSeries() == null || doc.getSeries().isBlank()) {
            errors.add("Serie requerida");
        }
        if (doc.getIssueDate() == null) {
            errors.add("Fecha de emisión requerida");
        }
        if (doc.getCurrency() == null || doc.getCurrency().isBlank()) {
            errors.add("Moneda requerida");
        } else if (!doc.getCurrency().matches("[A-Z]{3}")) {
            errors.add("Moneda debe ser un código ISO 4217 de tres letras mayúsculas");
        }
        // SUNAT electronic series have four alphanumeric characters and F/B family semantics.
        if (doc.getSeries() != null && !doc.getSeries().isBlank() && doc.getDocType() != null) {
            String normalized = doc.getSeries().toUpperCase();
            if (!normalized.matches("[FB][A-Z0-9]{3}")) {
                errors.add("Serie electrónica inválida: usa F/B y cuatro caracteres alfanuméricos");
            }
            String prefix = normalized.substring(0, 1);
            if (doc.getDocType() == DocumentType.INVOICE && !prefix.equals("F")) {
                errors.add("Serie de factura debe iniciar con F");
            }
            if (doc.getDocType() == DocumentType.RECEIPT && !prefix.equals("B")) {
                errors.add("Serie de boleta debe iniciar con B");
            }
        }
    }

    private void validateIssuer(Document doc, List<String> errors) {
        if (doc.getIssuerRuc() == null || !doc.getIssuerRuc().matches("\\d{11}")) {
            errors.add("RUC del emisor debe tener 11 dígitos");
        }
        if (doc.getIssuerName() == null || doc.getIssuerName().isBlank()) {
            errors.add("Razón social del emisor requerida");
        }
    }

    private void validateReceiver(Document doc, List<String> errors) {
        if (doc.getReceiverDocType() == null || doc.getReceiverDocType().isBlank()) {
            errors.add("Tipo de documento del receptor requerido");
        }
        if (doc.getReceiverDoc() == null || doc.getReceiverDoc().isBlank()) {
            errors.add("Número de documento del receptor requerido");
        }
        if (doc.getReceiverName() == null || doc.getReceiverName().isBlank()) {
            errors.add("Nombre del receptor requerido");
        }
        // Factura requires RUC (doc type 6)
        if (doc.getDocType() == DocumentType.INVOICE) {
            if (!"6".equals(doc.getReceiverDocType())) {
                errors.add("Factura requiere receptor con RUC (tipo documento 6)");
            }
            if (doc.getReceiverDoc() != null && doc.getReceiverDoc().length() != 11) {
                errors.add("RUC del receptor debe tener 11 dígitos");
            }
        }
        // Boleta: DNI (1) or anonymous
        if (doc.getDocType() == DocumentType.RECEIPT) {
            if ("6".equals(doc.getReceiverDocType())) {
                errors.add("Boleta no debe emitirse con RUC; usar Factura");
            }
        }
    }

    private void validateLines(Document doc, List<String> errors) {
        if (doc.getLines() == null || doc.getLines().isEmpty()) {
            errors.add("El documento debe tener al menos una línea");
            return;
        }
        for (int i = 0; i < doc.getLines().size(); i++) {
            DocumentLine line = doc.getLines().get(i);
            String prefix = "Línea " + (i + 1) + ": ";
            if (line.getProductName() == null || line.getProductName().isBlank()) {
                errors.add(prefix + "descripción del producto requerida");
            }
            if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(prefix + "cantidad debe ser mayor a 0");
            }
            if (line.getUnitPrice() == null || line.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(prefix + "precio unitario inválido");
            }
            if (line.getUnitCode() == null || line.getUnitCode().isBlank()) {
                errors.add(prefix + "unidad de medida requerida");
            }
            if (line.getTaxBase() == null || line.getTaxBase().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(prefix + "base imponible inválida");
            }
            if (line.getIgvAmount() == null || line.getIgvAmount().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(prefix + "IGV inválido");
            }
            var profile = PeruTaxRules.profile(line.getAffectationCode());
            if (profile == null) {
                errors.add(prefix + "código de afectación IGV no soportado: " + line.getAffectationCode());
            } else if (line.getTaxBase() != null && line.getIgvAmount() != null) {
                BigDecimal expectedTax = line.getTaxBase().multiply(profile.rate())
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                if (line.getIgvAmount().subtract(expectedTax).abs().compareTo(new BigDecimal("0.02")) > 0) {
                    errors.add(prefix + "impuesto no cuadra para afectación " + line.getAffectationCode());
                }
            }
        }
    }

    private void validateTotals(Document doc, List<String> errors) {
        if (doc.getTotal() == null || doc.getTotal().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Total del documento inválido");
        }
        if (doc.getTaxBase() == null || doc.getTaxBase().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Base imponible inválida");
        }
        // Mixed taxable/exempt/unaffected documents reconcile from their lines.
        if (doc.getLines() != null && doc.getIgvAmount() != null) {
            BigDecimal expectedIgv = doc.getLines().stream()
                .map(DocumentLine::getIgvAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal diff = doc.getIgvAmount().subtract(expectedIgv).abs();
            if (diff.compareTo(new BigDecimal("0.02")) > 0) {
                errors.add("IGV no cuadra: esperado " + expectedIgv + ", recibido " + doc.getIgvAmount());
            }
        }
        if (doc.getLines() != null && doc.getTaxBase() != null) {
            BigDecimal expectedBase = doc.getLines().stream()
                .map(DocumentLine::getTaxBase)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (doc.getTaxBase().subtract(expectedBase).abs().compareTo(new BigDecimal("0.02")) > 0) {
                errors.add("Base imponible no cuadra: esperado " + expectedBase + ", recibido " + doc.getTaxBase());
            }
        }
        if (doc.getTotal() != null && doc.getTaxBase() != null && doc.getIgvAmount() != null) {
            BigDecimal expectedTotal = doc.getTaxBase().add(doc.getIgvAmount())
                .add(valueOrZero(doc.getIscAmount())).add(valueOrZero(doc.getOtherTaxes()))
                .subtract(valueOrZero(doc.getDiscountTotal()));
            if (doc.getTotal().subtract(expectedTotal).abs().compareTo(new BigDecimal("0.02")) > 0) {
                errors.add("Total no cuadra: esperado " + expectedTotal + ", recibido " + doc.getTotal());
            }
        }
    }

    private void validateReference(Document doc, List<String> errors) {
        if (doc.getDocType() == DocumentType.CREDIT_NOTE || doc.getDocType() == DocumentType.DEBIT_NOTE) {
            if (doc.getRefDocType() == null || doc.getRefDocType().isBlank()) {
                errors.add("Nota requiere tipo de documento de referencia");
            } else if (!java.util.Set.of("01", "03").contains(doc.getRefDocType())) {
                errors.add("Nota solo puede referenciar factura (01) o boleta (03)");
            }
            if (doc.getRefSeries() == null || doc.getRefSeries().isBlank()) {
                errors.add("Nota requiere serie del documento de referencia");
            }
            if (doc.getRefNumber() == null || doc.getRefNumber() <= 0) {
                errors.add("Nota requiere número del documento de referencia");
            }
            if (doc.getRefReason() == null || doc.getRefReason().isBlank()) {
                errors.add("Nota requiere motivo");
            }
            java.util.Set<String> acceptedCodes = doc.getDocType() == DocumentType.CREDIT_NOTE
                ? CREDIT_NOTE_REASON_CODES : DEBIT_NOTE_REASON_CODES;
            if (doc.getRefReasonCode() == null || !acceptedCodes.contains(doc.getRefReasonCode())) {
                errors.add("Código de motivo inválido para "
                    + (doc.getDocType() == DocumentType.CREDIT_NOTE ? "catálogo SUNAT 09" : "catálogo SUNAT 10"));
            }
            if (doc.getRefSeries() != null && !doc.getRefSeries().matches("[FB][A-Z0-9]{3}")) {
                errors.add("Serie del documento de referencia inválida");
            }
            if (doc.getRefDocType() != null && doc.getRefSeries() != null && !doc.getRefSeries().isBlank()) {
                if ("01".equals(doc.getRefDocType()) && !doc.getRefSeries().startsWith("F")) {
                    errors.add("La factura referenciada debe tener serie F");
                }
                if ("03".equals(doc.getRefDocType()) && !doc.getRefSeries().startsWith("B")) {
                    errors.add("La boleta referenciada debe tener serie B");
                }
                if (doc.getSeries() != null && !doc.getSeries().isBlank()
                    && doc.getSeries().charAt(0) != doc.getRefSeries().charAt(0)) {
                    errors.add("La nota y el comprobante referenciado deben pertenecer a la misma familia F/B");
                }
            }
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void validateDateRange(Document doc, List<String> errors) {
        if (doc.getIssueDate() != null) {
            LocalDate today = today();
            // SUNAT allows up to 7 days in the past for invoices
            if (doc.getIssueDate().isBefore(today.minusDays(7))) {
                errors.add("Fecha de emisión fuera del plazo permitido (máximo 7 días atrás)");
            }
            if (doc.getIssueDate().isAfter(today.plusDays(1))) {
                errors.add("Fecha de emisión no puede ser futura");
            }
        }
    }

    private LocalDate today() {
        return LocalDate.now(PERU_ZONE);
    }
}
