package io.carabo.domain.reporting;

import io.carabo.domain.RuleViolation;
import java.time.DateTimeException;
import java.time.LocalDate;

public record ReportPeriod(LocalDate from, LocalDate to) {
    public ReportPeriod {
        if (from == null || to == null) invalid("from and to are required");
        if (to.isBefore(from)) invalid("to must not be before from");
    }

    public static ReportPeriod month(int year, int month) {
        if (year < 1 || month < 1 || month > 12) invalid("year and month are required and must identify a valid month");
        try {
            LocalDate from = LocalDate.of(year, month, 1);
            return new ReportPeriod(from, from.withDayOfMonth(from.lengthOfMonth()));
        } catch (DateTimeException error) {
            throw new RuleViolation("INVALID_REPORT_PERIOD", "Invalid report period", RuleViolation.Kind.INVALID);
        }
    }

    private static void invalid(String message) {
        throw new RuleViolation("INVALID_REPORT_PERIOD", message, RuleViolation.Kind.INVALID);
    }
}
