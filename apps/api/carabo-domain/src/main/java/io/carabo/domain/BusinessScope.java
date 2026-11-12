package io.carabo.domain;

/** Company authorized by the entry adapter for one operation. */
public record BusinessScope(String businessCode) {
    public BusinessScope {
        if (businessCode == null || businessCode.isBlank() || businessCode.length() > 50) {
            throw new RuleViolation("INVALID_BUSINESS", "Empresa requerida", RuleViolation.Kind.INVALID);
        }
    }
}
