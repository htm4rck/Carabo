package io.carabo.application.reporting;

import io.carabo.application.reporting.ReportContracts.DaySummary;
import io.carabo.application.reporting.ReportContracts.ExportRow;
import io.carabo.application.reporting.ReportContracts.TypeSummary;
import io.carabo.application.reporting.ports.ReportReader;
import io.carabo.application.reporting.ports.SalesWorkbook;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.RuleViolation;
import io.carabo.domain.reporting.ReportPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportUseCasesTest {
    private final BusinessScope company = new BusinessScope("TENANT_A");
    private final ReportPeriod january = new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    @Test void aggregatesTheAcceptedTypeSummariesWithExactDecimals() {
        var reader = new Reader();
        reader.types = List.of(new TypeSummary("INVOICE", 2, money("200.10"), money("36.02"), money("236.12")),
            new TypeSummary("RECEIPT", 1, money("0.10"), money("0.02"), money("0.12")));
        var report = new ReportUseCases(reader, unusedWorkbook()).sales(company, january);
        assertEquals("TENANT_A", report.businessCode());
        assertEquals(3, report.totalDocuments());
        assertEquals(money("200.20"), report.totalTaxBase());
        assertEquals(money("36.04"), report.totalIgv());
        assertEquals(money("236.24"), report.totalAmount());
        assertEquals(company, reader.scope);
        assertEquals(january, reader.period);
        assertThrows(UnsupportedOperationException.class, () -> report.byType().clear());
    }

    @Test void returnsEmptyTotalsWithoutInventingDocuments() {
        var report = new ReportUseCases(new Reader(), unusedWorkbook()).sales(company, january);
        assertEquals(0, report.totalDocuments());
        assertEquals(BigDecimal.ZERO, report.totalAmount());
        assertTrue(report.byType().isEmpty());
    }

    @Test void boundsTheMonthlyQueryIncludingLeapDay() {
        var reader = new Reader();
        reader.days = List.of(new DaySummary("2024-02-29", 2, money("10.00")));
        var report = new ReportUseCases(reader, unusedWorkbook()).monthly(company, 2024, 2);
        assertEquals(LocalDate.of(2024, 2, 1), reader.period.from());
        assertEquals(LocalDate.of(2024, 2, 29), reader.period.to());
        assertEquals(money("10.00"), report.totalAmount());
        assertEquals(2, report.totalDocuments());
    }

    @Test void rejectsInvalidPeriodsBeforeReadingData() {
        var reader = new Reader();
        var useCases = new ReportUseCases(reader, unusedWorkbook());
        for (int month : new int[] {0, -1, 13}) assertThrows(RuleViolation.class, () -> useCases.monthly(company, 2026, month));
        assertThrows(RuleViolation.class, () -> useCases.monthly(company, 0, 1));
        assertThrows(RuleViolation.class, () -> new ReportPeriod(january.to(), january.from()));
        assertThrows(RuleViolation.class, () -> new BusinessScope(" "));
        assertNull(reader.scope);
    }

    @Test void streamsTheSameAuthorizedCompanyIntoTheWorkbook() {
        var reader = new Reader();
        var collected = new ArrayList<ExportRow>();
        SalesWorkbook workbook = (scope, period, source) -> {
            assertEquals(company, scope);
            assertEquals(january, period);
            assertEquals(1, source.writeTo(collected::add));
            return new byte[] {1, 2};
        };
        reader.rows = List.of(new ExportRow(january.from(), DocumentType.INVOICE, "F001", 7,
            "20123456789", "Cliente", "PEN", money("100"), money("18"), money("118"), DocumentStatus.ACCEPTED, "digest"));
        assertArrayEquals(new byte[] {1, 2}, new ReportUseCases(reader, workbook).export(company, january));
        assertEquals("F001-00000007", collected.getFirst().fullNumber());
        assertEquals(company, reader.scope);
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private SalesWorkbook unusedWorkbook() { return (scope, period, rows) -> { throw new AssertionError("Workbook must not be invoked"); }; }

    private static final class Reader implements ReportReader {
        BusinessScope scope;
        ReportPeriod period;
        List<TypeSummary> types = List.of();
        List<DaySummary> days = List.of();
        List<ExportRow> rows = List.of();
        @Override public List<TypeSummary> byType(BusinessScope scope, ReportPeriod period) { this.scope = scope; this.period = period; return types; }
        @Override public List<DaySummary> byDay(BusinessScope scope, ReportPeriod period) { this.scope = scope; this.period = period; return days; }
        @Override public long scanAccepted(BusinessScope scope, ReportPeriod period, Consumer<ExportRow> consumer) {
            this.scope = scope; this.period = period;
            rows.forEach(consumer);
            return rows.size();
        }
    }
}
