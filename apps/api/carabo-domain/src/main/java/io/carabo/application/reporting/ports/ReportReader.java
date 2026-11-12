package io.carabo.application.reporting.ports;

import io.carabo.application.reporting.ReportContracts.DaySummary;
import io.carabo.application.reporting.ReportContracts.ExportRow;
import io.carabo.application.reporting.ReportContracts.TypeSummary;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.reporting.ReportPeriod;
import java.util.List;
import java.util.function.Consumer;

/** Accepted fiscal documents, consistently scoped to one authorized company. */
public interface ReportReader {
    List<TypeSummary> byType(BusinessScope scope, ReportPeriod period);
    List<DaySummary> byDay(BusinessScope scope, ReportPeriod period);
    /** Delivers rows synchronously within the adapter's read transaction; closes the cursor on success or failure. */
    long scanAccepted(BusinessScope scope, ReportPeriod period, Consumer<ExportRow> rows);
}
