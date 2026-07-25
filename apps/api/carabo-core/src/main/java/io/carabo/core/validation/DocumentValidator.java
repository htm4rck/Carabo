package io.carabo.core.validation;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a Document against tax authority rules before XML generation.
 * Returns a list of errors. Empty list = valid.
 */
public class DocumentValidator {

    public List<String> validate(Document doc, Tenant tenant) {
        List<String> errors = new ArrayList<>();

        validateTenant(tenant, errors);
        validateHeader(doc, errors);
        validateIssuer(doc, errors);
        validateReceiver(doc, errors);
        validateLines(doc, errors);
        validateTotals(doc, errors);
        validateReference(doc, errors);
        validateDateRange(doc, errors);

        return errors;
    }

    private void validateTenant(Tenant tenant, List<String> errors) {
        if (!tenant.isActive()) {
            errors.add("Tenant desactivado: " + tenant.getBusinessCode());
        }
        if (tenant.getCertificateFile() == null || tenant.getCertificateFile().isBlank()) {
            errors.add("Certificado digital no configurado");
        }
        if (tenant.getCertificateValidTo() != null && tenant.getCertificateValidTo().isBefore(LocalDate.now())) {
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
        }
        // Validate series prefix matches doc type
        if (doc.getSeries() != null && doc.getDocType() != null) {
            String prefix = doc.getSeries().substring(0, 1).toUpperCase();
            if (doc.getDocType() == DocumentType.INVOICE && !prefix.equals("F")) {
                errors.add("Serie de factura debe iniciar con F");
            }
            if (doc.getDocType() == DocumentType.RECEIPT && !prefix.equals("B")) {
                errors.add("Serie de boleta debe iniciar con B");
            }
        }
    }

    private void validateIssuer(Document doc, List<String> errors) {
        if (doc.getIssuerRuc() == null || doc.getIssuerRuc().length() != 11) {
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
        }
    }

    private void validateTotals(Document doc, List<String> errors) {
        if (doc.getTotal() == null || doc.getTotal().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Total del documento inválido");
        }
        if (doc.getTaxBase() == null || doc.getTaxBase().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Base imponible inválida");
        }
        // Verify IGV = taxBase * 0.18 (with tolerance)
        if (doc.getTaxBase() != null && doc.getIgvAmount() != null) {
            BigDecimal expectedIgv = doc.getTaxBase().multiply(new BigDecimal("0.18"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal diff = doc.getIgvAmount().subtract(expectedIgv).abs();
            if (diff.compareTo(new BigDecimal("0.02")) > 0) {
                errors.add("IGV no cuadra: esperado " + expectedIgv + ", recibido " + doc.getIgvAmount());
            }
        }
    }

    private void validateReference(Document doc, List<String> errors) {
        if (doc.getDocType() == DocumentType.CREDIT_NOTE || doc.getDocType() == DocumentType.DEBIT_NOTE) {
            if (doc.getRefDocType() == null || doc.getRefDocType().isBlank()) {
                errors.add("Nota requiere tipo de documento de referencia");
            }
            if (doc.getRefSeries() == null || doc.getRefSeries().isBlank()) {
                errors.add("Nota requiere serie del documento de referencia");
            }
            if (doc.getRefNumber() == null) {
                errors.add("Nota requiere número del documento de referencia");
            }
            if (doc.getRefReason() == null || doc.getRefReason().isBlank()) {
                errors.add("Nota requiere motivo");
            }
        }
    }

    private void validateDateRange(Document doc, List<String> errors) {
        if (doc.getIssueDate() != null) {
            LocalDate today = LocalDate.now();
            // SUNAT allows up to 7 days in the past for invoices
            if (doc.getIssueDate().isBefore(today.minusDays(7))) {
                errors.add("Fecha de emisión fuera del plazo permitido (máximo 7 días atrás)");
            }
            if (doc.getIssueDate().isAfter(today.plusDays(1))) {
                errors.add("Fecha de emisión no puede ser futura");
            }
        }
    }
}
