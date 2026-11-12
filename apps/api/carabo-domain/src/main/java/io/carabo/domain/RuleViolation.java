package io.carabo.domain;

public final class RuleViolation extends RuntimeException {
    public enum Kind { INVALID, MISSING, CONFLICT }

    private final String code;
    private final Kind kind;

    public RuleViolation(String code, String message, Kind kind) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public String code() { return code; }
    public Kind kind() { return kind; }
}
