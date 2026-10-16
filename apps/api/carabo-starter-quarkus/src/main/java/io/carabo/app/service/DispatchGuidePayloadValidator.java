package io.carabo.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.carabo.app.service.IntegrationPayloadValidator.ValidationIssue;
import io.carabo.app.service.IntegrationPayloadValidator.ValidationReport;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Validates a GRE 2.0 producer payload without emitting or reserving a correlative. */
@ApplicationScoped
public class DispatchGuidePayloadValidator {
    private static final Set<String> ROOT_FIELDS = Set.of("businessCode", "series", "issueDate", "startDate",
        "receiver", "transportMode", "grossWeight", "weightUnit", "packageCount", "origin", "destination",
        "carrier", "driver", "vehiclePlate", "transferReasonCode", "transferReasonDesc", "relatedDoc",
        "items", "callbackUrl", "externalId");
    private static final Set<String> RECEIVER_FIELDS = Set.of("docType", "doc", "name");
    private static final Set<String> ADDRESS_FIELDS = Set.of("ubigeo", "address");
    private static final Set<String> CARRIER_FIELDS = Set.of("ruc", "name");
    private static final Set<String> DRIVER_FIELDS = Set.of("docType", "doc", "name", "license");
    private static final Set<String> RELATED_FIELDS = Set.of("docType", "number");
    private static final Set<String> ITEM_FIELDS = Set.of("productCode", "productName", "unitCode", "quantity", "weight");
    private static final Set<String> TRANSFER_REASONS = Set.of(
        "01", "02", "03", "04", "05", "06", "07", "08", "09", "13", "14", "17", "18", "19");

    public ValidationReport validate(JsonNode payload, String idempotencyKey, LocalDate fiscalToday) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (payload == null || !payload.isObject()) {
            error(issues, "$", "INVALID_JSON_OBJECT", "El cuerpo debe ser un objeto JSON", "{ ... }", value(payload),
                "Envía una guía con receptor, traslado, ruta e items.");
            return report(issues);
        }

        flagUnknown(payload, ROOT_FIELDS, "$", issues);
        String series = requiredText(payload, "series", "$.series", issues, "T001");
        if (series != null && !series.matches("T[A-Z0-9]{3}")) error(issues, "$.series", "INVALID_GRE_SERIES",
            "La serie GRE no tiene formato válido", "T001", series, "Usa cuatro caracteres iniciando con T.");
        LocalDate issueDate = date(payload, "issueDate", "$.issueDate", fiscalToday, issues);
        LocalDate startDate = date(payload, "startDate", "$.startDate", fiscalToday, issues);
        if (issueDate != null && startDate != null && startDate.isBefore(issueDate)) error(issues, "$.startDate",
            "START_BEFORE_ISSUE", "El traslado no puede iniciar antes de la emisión", issueDate.toString(), startDate.toString(),
            "Usa la fecha de emisión o una fecha posterior.");

        objectWithRequiredText(payload.get("receiver"), "$.receiver", RECEIVER_FIELDS,
            List.of(new Required("docType", "tipo de documento"), new Required("doc", "número de documento"),
                new Required("name", "nombre o razón social")), issues);
        address(payload.get("origin"), "$.origin", issues);
        address(payload.get("destination"), "$.destination", issues);

        String transportMode = requiredText(payload, "transportMode", "$.transportMode", issues, "01 público o 02 privado");
        if (transportMode != null && !Set.of("01", "02").contains(transportMode)) error(issues, "$.transportMode",
            "INVALID_TRANSPORT_MODE", "Modalidad de transporte no admitida", "01 o 02", transportMode,
            "Usa 01 para transporte público o 02 para transporte privado.");
        positiveNumber(payload, "grossWeight", "$.grossWeight", false, issues);
        positiveNumber(payload, "packageCount", "$.packageCount", true, issues);
        String weightUnit = requiredText(payload, "weightUnit", "$.weightUnit", issues, "KGM");
        if (weightUnit != null && !"KGM".equals(weightUnit)) warning(issues, "$.weightUnit", "NON_STANDARD_WEIGHT_UNIT",
            "La unidad habitual de peso GRE es KGM", "KGM", weightUnit, "Confirma que SUNAT admite la unidad enviada.");

        if ("01".equals(transportMode)) {
            JsonNode carrier = payload.get("carrier");
            objectWithRequiredText(carrier, "$.carrier", CARRIER_FIELDS,
                List.of(new Required("ruc", "RUC de 11 dígitos"), new Required("name", "razón social del transportista")), issues);
            String ruc = text(carrier, "ruc");
            if (ruc != null && !ruc.matches("\\d{11}")) error(issues, "$.carrier.ruc", "INVALID_CARRIER_RUC",
                "El RUC del transportista debe tener 11 dígitos", "20123456789", ruc, "Corrige el RUC sin espacios ni guiones.");
        }
        if ("02".equals(transportMode)) {
            objectWithRequiredText(payload.get("driver"), "$.driver", DRIVER_FIELDS,
                List.of(new Required("docType", "tipo de documento"), new Required("doc", "documento del conductor"),
                    new Required("name", "nombre del conductor"), new Required("license", "licencia de conducir")), issues);
            requiredText(payload, "vehiclePlate", "$.vehiclePlate", issues, "ABC123");
        }

        String reason = requiredText(payload, "transferReasonCode", "$.transferReasonCode", issues, "catálogo SUNAT de traslado");
        if (reason != null && !TRANSFER_REASONS.contains(reason)) error(issues, "$.transferReasonCode", "INVALID_TRANSFER_REASON",
            "Motivo de traslado no admitido", String.join(", ", TRANSFER_REASONS), reason,
            "Selecciona el código SUNAT que corresponde al traslado.");
        requiredText(payload, "transferReasonDesc", "$.transferReasonDesc", issues, "descripción del motivo de traslado");

        JsonNode related = payload.get("relatedDoc");
        if (related != null && !related.isNull()) objectWithRequiredText(related, "$.relatedDoc", RELATED_FIELDS,
            List.of(new Required("docType", "tipo de comprobante"), new Required("number", "serie-número del comprobante")), issues);

        JsonNode items = payload.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            error(issues, "$.items", "ITEMS_REQUIRED", "La guía debe contener al menos un bien trasladado",
                "arreglo con uno o más items", value(items), "Agrega los bienes transportados.");
        } else {
            for (int index = 0; index < items.size(); index++) item(items.get(index), index, issues);
        }

        callback(payload.get("callbackUrl"), issues);
        idempotency(payload, idempotencyKey, issues);
        if (text(payload, "businessCode") != null) warning(issues, "$.businessCode", "TENANT_DERIVED",
            "La empresa enviada será ignorada", "empresa derivada de X-Api-Key", text(payload, "businessCode"),
            "Omite businessCode para evitar cruces de tenant.");
        return report(issues);
    }

    private void address(JsonNode node, String path, List<ValidationIssue> issues) {
        objectWithRequiredText(node, path, ADDRESS_FIELDS,
            List.of(new Required("ubigeo", "ubigeo de 6 dígitos"), new Required("address", "dirección completa")), issues);
        String ubigeo = text(node, "ubigeo");
        if (ubigeo != null && !ubigeo.matches("\\d{6}")) error(issues, path + ".ubigeo", "INVALID_UBIGEO",
            "El ubigeo debe tener 6 dígitos", "150101", ubigeo, "Usa el código INEI del punto de traslado.");
    }

    private void item(JsonNode node, int index, List<ValidationIssue> issues) {
        String path = "$.items[" + index + "]";
        if (node == null || !node.isObject()) {
            error(issues, path, "INVALID_ITEM", "Cada item debe ser un objeto", "{ productName, unitCode, quantity }",
                value(node), "Reemplaza el item por un objeto JSON.");
            return;
        }
        flagUnknown(node, ITEM_FIELDS, path, issues);
        requiredText(node, "productName", path + ".productName", issues, "descripción del bien");
        String unit = requiredText(node, "unitCode", path + ".unitCode", issues, "NIU");
        if (unit != null && !unit.matches("[A-Z0-9]{3}")) error(issues, path + ".unitCode", "INVALID_UNIT_CODE",
            "La unidad debe usar un código de tres caracteres", "NIU", unit, "Usa el código de unidad SUNAT aplicable.");
        positiveNumber(node, "quantity", path + ".quantity", false, issues);
        if (node.hasNonNull("weight")) positiveNumber(node, "weight", path + ".weight", false, issues);
    }

    private void objectWithRequiredText(JsonNode node, String path, Set<String> allowed, List<Required> required,
                                        List<ValidationIssue> issues) {
        if (node == null || !node.isObject()) {
            error(issues, path, "OBJECT_REQUIRED", "Se requiere un objeto", allowed.toString(), value(node),
                "Agrega el objeto con sus campos obligatorios.");
            return;
        }
        flagUnknown(node, allowed, path, issues);
        required.forEach(field -> requiredText(node, field.name(), path + "." + field.name(), issues, field.expected()));
    }

    private LocalDate date(JsonNode payload, String field, String path, LocalDate fiscalToday, List<ValidationIssue> issues) {
        String raw = requiredText(payload, field, path, issues, "YYYY-MM-DD");
        if (raw == null) return null;
        try {
            LocalDate parsed = LocalDate.parse(raw);
            if (fiscalToday != null && (parsed.isBefore(fiscalToday.minusDays(7)) || parsed.isAfter(fiscalToday.plusDays(30)))) {
                error(issues, path, "DATE_OUT_OF_RANGE", "La fecha está fuera del rango operativo",
                    fiscalToday.minusDays(7) + " a " + fiscalToday.plusDays(30), raw,
                    "Confirma la fecha fiscal en America/Lima.");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            error(issues, path, "INVALID_DATE", "La fecha no tiene formato ISO", "YYYY-MM-DD", raw,
                "Ejemplo: " + fiscalToday + ".");
            return null;
        }
    }

    private void idempotency(JsonNode payload, String header, List<ValidationIssue> issues) {
        String externalId = text(payload, "externalId");
        String normalized = blankToNull(header);
        if (normalized == null && externalId == null) error(issues, "$.externalId", "IDEMPOTENCY_REQUIRED",
            "La guía requiere una clave idempotente", "externalId y header Idempotency-Key iguales", null,
            "Genera un identificador estable en el sistema productor.");
        else if (normalized != null && externalId != null && !normalized.equals(externalId)) error(issues, "$.externalId",
            "IDEMPOTENCY_CONFLICT", "externalId no coincide con Idempotency-Key", normalized, externalId,
            "Usa exactamente el mismo valor en el header y el cuerpo.");
        String effective = normalized != null ? normalized : externalId;
        if (effective != null && effective.length() > 100) error(issues, "$.externalId", "IDEMPOTENCY_TOO_LONG",
            "La clave idempotente excede 100 caracteres", "máximo 100 caracteres", String.valueOf(effective.length()),
            "Usa un identificador estable más corto.");
    }

    private void callback(JsonNode node, List<ValidationIssue> issues) {
        if (node == null || node.isNull()) return;
        try {
            URI uri = URI.create(node.isTextual() ? node.asText() : "");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            error(issues, "$.callbackUrl", "INVALID_CALLBACK", "callbackUrl debe ser una URL HTTPS pública",
                "https://erp.example.com/webhooks/carabo", value(node), "Corrige la URL o elimina el campo.");
        }
    }

    private void positiveNumber(JsonNode object, String field, String path, boolean integer, List<ValidationIssue> issues) {
        JsonNode node = object == null ? null : object.get(field);
        if (node == null || !node.isNumber()) {
            error(issues, path, "NUMBER_REQUIRED", "Se requiere un valor numérico", "número mayor a 0", value(node),
                "Envía el valor como número JSON, sin comillas.");
            return;
        }
        BigDecimal number = node.decimalValue();
        if (number.signum() <= 0 || (integer && number.stripTrailingZeros().scale() > 0)) error(issues, path, "INVALID_NUMBER",
            "El valor está fuera del rango permitido", integer ? "entero mayor a 0" : "número mayor a 0",
            number.toPlainString(), "Corrige la cantidad.");
    }

    private String requiredText(JsonNode object, String field, String path, List<ValidationIssue> issues, String expected) {
        String result = text(object, field);
        if (result == null) error(issues, path, "REQUIRED", "Campo obligatorio ausente o vacío", expected,
            value(object == null ? null : object.get(field)), "Agrega el campo con el formato indicado.");
        return result;
    }

    private String text(JsonNode object, String field) {
        if (object == null) return null;
        JsonNode node = object.get(field);
        return node != null && node.isTextual() ? blankToNull(node.asText()) : null;
    }

    private void flagUnknown(JsonNode object, Set<String> allowed, String path, List<ValidationIssue> issues) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) warning(issues, path + "." + name, "UNKNOWN_FIELD", "Carabo no reconoce este campo",
                String.join(", ", allowed), value(object.get(name)), "Elimina el campo o verifica el nombre en el contrato.");
        }
    }

    private ValidationReport report(List<ValidationIssue> issues) {
        return new ValidationReport(issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity())), issues);
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String value(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }

    private void error(List<ValidationIssue> issues, String path, String code, String message, String expected,
                       String received, String suggestion) {
        issues.add(new ValidationIssue(path, code, "ERROR", message, expected, received, suggestion));
    }

    private void warning(List<ValidationIssue> issues, String path, String code, String message, String expected,
                         String received, String suggestion) {
        issues.add(new ValidationIssue(path, code, "WARNING", message, expected, received, suggestion));
    }

    private record Required(String name, String expected) {}
}
