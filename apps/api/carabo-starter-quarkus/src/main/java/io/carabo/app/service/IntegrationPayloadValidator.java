package io.carabo.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Validates the external producer contract without emitting or reserving a correlative. */
@ApplicationScoped
public class IntegrationPayloadValidator {
    private static final Set<String> DOCUMENT_TYPES = Set.of("01", "03", "07", "08");
    private static final Set<String> AFFECTATION_CODES = Set.of("10", "20", "30", "40");
    private static final Set<String> CREDIT_REASON_CODES = Set.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13");
    private static final Set<String> DEBIT_REASON_CODES = Set.of("01", "02", "03", "11", "12");
    private static final Set<String> ROOT_FIELDS = Set.of("docType", "series", "issueDate", "dueDate",
        "currency", "sendMode", "issuer", "receiver", "lines", "reference", "callbackUrl",
        "externalId", "sourceDocId");
    private static final Set<String> RECEIVER_FIELDS = Set.of("docType", "doc", "name", "address");
    private static final Set<String> LINE_FIELDS = Set.of("productCode", "productName", "unitCode",
        "quantity", "unitPrice", "discount", "affectationCode");
    private static final Set<String> REFERENCE_FIELDS = Set.of("docType", "series", "number", "reasonCode", "reason");

    public ValidationReport validate(JsonNode payload, String idempotencyKey, LocalDate fiscalToday) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (payload == null || !payload.isObject()) {
            error(issues, "$", "INVALID_JSON_OBJECT", "El cuerpo debe ser un objeto JSON", "{ ... }", value(payload),
                "Envía un objeto con docType, receiver y lines.");
            return report(issues);
        }

        flagUnknown(payload, ROOT_FIELDS, "$", issues);
        String docType = requiredText(payload, "docType", "$.docType", issues, "01, 03, 07 u 08");
        if (docType != null && !DOCUMENT_TYPES.contains(docType)) {
            error(issues, "$.docType", "UNSUPPORTED_DOCUMENT_TYPE", "Tipo de documento no soportado",
                "01, 03, 07 u 08", docType, "Usa el código SUNAT del comprobante.");
        }

        String series = text(payload, "series");
        if (series == null) {
            warning(issues, "$.series", "DEFAULTED_SERIES", "La serie no fue enviada",
                docType != null && docType.equals("03") ? "B001" : "F001", null,
                "Carabo usará la serie predeterminada del tenant; envíala si necesitas una serie concreta.");
        } else if (!series.matches("[FB][A-Z0-9]{3}")) {
            error(issues, "$.series", "INVALID_SERIES", "La serie electrónica no tiene formato válido",
                "F001 o B001", series, "Usa cuatro caracteres, iniciando con F o B.");
        } else if ("01".equals(docType) && !series.startsWith("F")) {
            error(issues, "$.series", "INVOICE_SERIES", "Una factura requiere serie F", "F001", series,
                "Cambia la serie para que inicie con F.");
        } else if ("03".equals(docType) && !series.startsWith("B")) {
            error(issues, "$.series", "RECEIPT_SERIES", "Una boleta requiere serie B", "B001", series,
                "Cambia la serie para que inicie con B.");
        }

        validateDate(payload, "issueDate", "$.issueDate", fiscalToday, issues);
        validateDate(payload, "dueDate", "$.dueDate", null, issues);
        String currency = text(payload, "currency");
        if (currency == null) {
            warning(issues, "$.currency", "DEFAULTED_CURRENCY", "La moneda no fue enviada", "PEN", null,
                "Carabo usará PEN; envía el código ISO 4217 para hacerlo explícito.");
        } else if (!currency.matches("[A-Z]{3}")) {
            error(issues, "$.currency", "INVALID_CURRENCY", "La moneda debe usar ISO 4217", "PEN", currency,
                "Usa tres letras mayúsculas.");
        }

        JsonNode receiver = payload.get("receiver");
        if (receiver == null || !receiver.isObject()) {
            error(issues, "$.receiver", "RECEIVER_REQUIRED", "El receptor es obligatorio",
                "objeto con docType, doc y name", value(receiver), "Agrega los datos de identificación del receptor.");
        } else {
            flagUnknown(receiver, RECEIVER_FIELDS, "$.receiver", issues);
            String receiverType = requiredText(receiver, "docType", "$.receiver.docType", issues, "6 para RUC o 1 para DNI");
            String receiverDoc = requiredText(receiver, "doc", "$.receiver.doc", issues, "número de documento");
            requiredText(receiver, "name", "$.receiver.name", issues, "razón social o nombre");
            if ("01".equals(docType) && !"6".equals(receiverType)) {
                error(issues, "$.receiver.docType", "INVOICE_REQUIRES_RUC", "Una factura requiere receptor con RUC",
                    "6", receiverType, "Usa tipo 6 o cambia docType a 03 si corresponde una boleta.");
            }
            if ("01".equals(docType) && receiverDoc != null && !receiverDoc.matches("\\d{11}")) {
                error(issues, "$.receiver.doc", "INVALID_RECEIVER_RUC", "El RUC del receptor debe tener 11 dígitos",
                    "20131312955", receiverDoc, "Corrige el RUC sin espacios ni guiones.");
            }
        }

        JsonNode lines = payload.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            error(issues, "$.lines", "LINES_REQUIRED", "El documento debe tener al menos una línea",
                "arreglo con uno o más conceptos", value(lines), "Agrega el producto o servicio facturado.");
        } else {
            for (int index = 0; index < lines.size(); index++) validateLine(lines.get(index), index, issues);
        }

        if ("07".equals(docType) || "08".equals(docType)) validateReference(payload.get("reference"), docType, issues);
        validateCallback(payload.get("callbackUrl"), issues);
        validateIdempotency(payload, idempotencyKey, issues);

        if (payload.has("issuer")) {
            warning(issues, "$.issuer", "TENANT_ISSUER_PREFERRED", "El emisor puede omitirse",
                "emisor configurado en Carabo", "issuer enviado", "Omite issuer para evitar divergencias con la configuración fiscal.");
        }
        if (payload.has("sendMode")) {
            warning(issues, "$.sendMode", "TENANT_CHANNEL_PREFERRED", "El canal enviado no modifica la configuración fiscal",
                "canal SUNAT/OSE configurado en Carabo", value(payload.get("sendMode")),
                "Omite sendMode; Admin/Root controla el canal de la empresa.");
        }
        return report(issues);
    }

    private void validateLine(JsonNode line, int index, List<ValidationIssue> issues) {
        String base = "$.lines[" + index + "]";
        if (line == null || !line.isObject()) {
            error(issues, base, "INVALID_LINE", "Cada línea debe ser un objeto", "{ productName, quantity, unitPrice }",
                value(line), "Reemplaza la línea por un objeto JSON.");
            return;
        }
        flagUnknown(line, LINE_FIELDS, base, issues);
        requiredText(line, "productName", base + ".productName", issues, "descripción del producto o servicio");
        positiveNumber(line, "quantity", base + ".quantity", true, issues);
        positiveNumber(line, "unitPrice", base + ".unitPrice", false, issues);
        String unit = text(line, "unitCode");
        if (unit == null) warning(issues, base + ".unitCode", "DEFAULTED_UNIT", "La unidad no fue enviada", "NIU", null,
            "Carabo usará NIU; envíala para hacer explícita la unidad SUNAT.");
        String affectation = text(line, "affectationCode");
        if (affectation == null) warning(issues, base + ".affectationCode", "DEFAULTED_AFFECTATION",
            "La afectación IGV no fue enviada", "10", null, "Carabo usará gravado 10; confirma el tratamiento tributario.");
        else if (!AFFECTATION_CODES.contains(affectation)) error(issues, base + ".affectationCode", "UNSUPPORTED_AFFECTATION",
            "Código de afectación IGV no soportado", "10, 20, 30 o 40", affectation,
            "Selecciona el código SUNAT que corresponda al concepto.");
    }

    private void validateReference(JsonNode reference, String docType, List<ValidationIssue> issues) {
        if (reference == null || !reference.isObject()) {
            error(issues, "$.reference", "REFERENCE_REQUIRED", "Las notas requieren el comprobante de referencia",
                "objeto con docType, series, number, reasonCode y reason", value(reference),
                "Agrega los datos de la factura o boleta que modifica la nota.");
            return;
        }
        flagUnknown(reference, REFERENCE_FIELDS, "$.reference", issues);
        String referenceType = requiredText(reference, "docType", "$.reference.docType", issues, "01 o 03");
        if (referenceType != null && !Set.of("01", "03").contains(referenceType)) error(issues, "$.reference.docType",
            "INVALID_REFERENCE_TYPE", "La nota sólo puede referenciar factura o boleta", "01 o 03", referenceType,
            "Usa el tipo SUNAT del comprobante original.");
        String referenceSeries = requiredText(reference, "series", "$.reference.series", issues, "F001 o B001");
        if (referenceSeries != null && !referenceSeries.matches("[FB][A-Z0-9]{3}")) error(issues, "$.reference.series",
            "INVALID_REFERENCE_SERIES", "La serie de referencia no tiene formato válido", "F001 o B001", referenceSeries,
            "Usa la serie exacta del comprobante original.");
        positiveNumber(reference, "number", "$.reference.number", true, issues);
        String reasonCode = requiredText(reference, "reasonCode", "$.reference.reasonCode", issues,
            "catálogo SUNAT " + ("07".equals(docType) ? "09" : "10"));
        Set<String> acceptedReasons = "07".equals(docType) ? CREDIT_REASON_CODES : DEBIT_REASON_CODES;
        if (reasonCode != null && !acceptedReasons.contains(reasonCode)) error(issues, "$.reference.reasonCode",
            "INVALID_REASON_CODE", "Código de motivo no admitido para este tipo de nota", String.join(", ", acceptedReasons),
            reasonCode, "Selecciona el código del catálogo SUNAT correspondiente.");
        requiredText(reference, "reason", "$.reference.reason", issues, "motivo legible");
    }

    private void validateIdempotency(JsonNode payload, String header, List<ValidationIssue> issues) {
        String externalId = text(payload, "externalId");
        String normalizedHeader = blankToNull(header);
        if (normalizedHeader == null && externalId == null) {
            error(issues, "$.externalId", "IDEMPOTENCY_REQUIRED", "La ingestión requiere una clave idempotente",
                "externalId y header Idempotency-Key iguales", null,
                "Genera un identificador estable desde el documento del sistema productor.");
        } else if (normalizedHeader != null && externalId != null && !normalizedHeader.equals(externalId)) {
            error(issues, "$.externalId", "IDEMPOTENCY_CONFLICT", "externalId no coincide con Idempotency-Key",
                normalizedHeader, externalId, "Usa exactamente el mismo valor en el header y el cuerpo.");
        }
        String effective = normalizedHeader != null ? normalizedHeader : externalId;
        if (effective != null && effective.length() > 100) error(issues, "$.externalId", "IDEMPOTENCY_TOO_LONG",
            "La clave idempotente excede 100 caracteres", "máximo 100 caracteres", String.valueOf(effective.length()),
            "Usa un identificador estable más corto.");
    }

    private void validateDate(JsonNode payload, String field, String path, LocalDate fiscalToday, List<ValidationIssue> issues) {
        String raw = text(payload, field);
        if (raw == null) {
            if (field.equals("issueDate")) warning(issues, path, "DEFAULTED_DATE", "La fecha de emisión no fue enviada",
                fiscalToday.toString(), null, "Carabo usará la fecha fiscal de Lima; envíala para evitar ambigüedad.");
            return;
        }
        try {
            LocalDate parsed = LocalDate.parse(raw);
            if (fiscalToday != null && (parsed.isBefore(fiscalToday.minusDays(7)) || parsed.isAfter(fiscalToday.plusDays(1)))) {
                error(issues, path, "DATE_OUT_OF_RANGE", "La fecha está fuera del rango admitido",
                    fiscalToday.minusDays(7) + " a " + fiscalToday.plusDays(1), raw,
                    "Usa la fecha fiscal correspondiente en America/Lima.");
            }
        } catch (DateTimeParseException error) {
            error(issues, path, "INVALID_DATE", "La fecha no tiene formato ISO", "YYYY-MM-DD", raw,
                "Ejemplo: " + (fiscalToday != null ? fiscalToday : LocalDate.of(2026, 10, 16)) + ".");
        }
    }

    private void validateCallback(JsonNode callback, List<ValidationIssue> issues) {
        if (callback == null || callback.isNull()) return;
        if (!callback.isTextual()) {
            error(issues, "$.callbackUrl", "INVALID_CALLBACK", "callbackUrl debe ser texto", "https://...", value(callback),
                "Envía una URL HTTPS pública o elimina el campo.");
            return;
        }
        try {
            URI uri = URI.create(callback.asText());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            error(issues, "$.callbackUrl", "INVALID_CALLBACK", "callbackUrl debe ser una URL HTTPS pública",
                "https://erp.example.com/webhooks/carabo", callback.asText(), "Corrige la URL o elimina el campo.");
        }
    }

    private String requiredText(JsonNode object, String field, String path, List<ValidationIssue> issues, String expected) {
        String result = text(object, field);
        if (result == null) error(issues, path, "REQUIRED", "Campo obligatorio ausente o vacío", expected,
            value(object == null ? null : object.get(field)), "Agrega el campo con el formato indicado.");
        return result;
    }

    private void positiveNumber(JsonNode object, String field, String path, boolean strictlyPositive, List<ValidationIssue> issues) {
        JsonNode value = object.get(field);
        if (value == null || !value.isNumber()) {
            error(issues, path, "NUMBER_REQUIRED", "Se requiere un valor numérico", strictlyPositive ? "número mayor a 0" : "número mayor o igual a 0",
                value(value), "Envía el valor como número JSON, sin comillas.");
            return;
        }
        BigDecimal number = value.decimalValue();
        if ((strictlyPositive && number.signum() <= 0) || (!strictlyPositive && number.signum() < 0)) {
            error(issues, path, "INVALID_NUMBER", "El valor está fuera del rango permitido",
                strictlyPositive ? "mayor a 0" : "mayor o igual a 0", number.toPlainString(), "Corrige el importe o cantidad.");
        }
    }

    private void flagUnknown(JsonNode object, Set<String> allowed, String base, List<ValidationIssue> issues) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) warning(issues, base + "." + name, "UNKNOWN_FIELD", "Carabo no reconoce este campo",
                String.join(", ", allowed), value(object.get(name)), "Elimina el campo o verifica el nombre en el contrato.");
        }
    }

    private ValidationReport report(List<ValidationIssue> issues) {
        return new ValidationReport(issues.stream().noneMatch(issue -> issue.severity().equals("ERROR")), issues);
    }

    private String text(JsonNode object, String field) {
        if (object == null) return null;
        JsonNode value = object.get(field);
        return value != null && value.isTextual() ? blankToNull(value.asText()) : null;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String value(JsonNode value) {
        if (value == null || value.isNull()) return null;
        return value.isTextual() ? value.asText() : value.toString();
    }

    private void error(List<ValidationIssue> issues, String path, String code, String message, String expected,
                       String received, String suggestion) {
        issues.add(new ValidationIssue(path, code, "ERROR", message, expected, received, suggestion));
    }

    private void warning(List<ValidationIssue> issues, String path, String code, String message, String expected,
                         String received, String suggestion) {
        issues.add(new ValidationIssue(path, code, "WARNING", message, expected, received, suggestion));
    }

    public record ValidationIssue(String path, String code, String severity, String message,
                                  String expected, String received, String suggestion) {}
    public record ValidationReport(boolean valid, List<ValidationIssue> issues) {}
}
