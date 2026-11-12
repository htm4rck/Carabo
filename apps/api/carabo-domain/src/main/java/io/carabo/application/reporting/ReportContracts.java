package io.carabo.application.reporting;

import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportContracts {
    private ReportContracts() {}

    public record TypeSummary(String docType, long count, BigDecimal taxBase, BigDecimal igvAmount, BigDecimal total) {}
    public record DaySummary(String date, long count, BigDecimal total) {}
    public record SalesReport(String businessCode, String from, String to, long totalDocuments,
                              BigDecimal totalTaxBase, BigDecimal totalIgv, BigDecimal totalAmount,
                              List<TypeSummary> byType) {
        public SalesReport { byType = List.copyOf(byType); }
    }
    public record MonthlyReport(String businessCode, int year, int month, long totalDocuments,
                                BigDecimal totalAmount, List<DaySummary> days) {
        public MonthlyReport { days = List.copyOf(days); }
    }
    public record ExportRow(LocalDate issueDate, DocumentType docType, String series, Integer number,
                            String receiverDoc, String receiverName, String currency,
                            BigDecimal taxBase, BigDecimal igvAmount, BigDecimal total,
                            DocumentStatus status, String hash) {
        public String fullNumber() {
            return number == null ? null : series + "-" + String.format("%08d", number);
        }
    }
}
