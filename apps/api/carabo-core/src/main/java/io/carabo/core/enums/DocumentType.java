package io.carabo.core.enums;

public enum DocumentType {
    INVOICE("01", "Factura"),
    RECEIPT("03", "Boleta"),
    CREDIT_NOTE("07", "Nota de Crédito"),
    DEBIT_NOTE("08", "Nota de Débito"),
    DISPATCH_GUIDE("09", "Guía de Remisión"),
    VOIDED("RA", "Comunicación de Baja"),
    DAILY_SUMMARY("RC", "Resumen Diario");

    private final String sunatCode;
    private final String description;

    DocumentType(String sunatCode, String description) {
        this.sunatCode = sunatCode;
        this.description = description;
    }

    public String sunatCode() { return sunatCode; }
    public String description() { return description; }
}
