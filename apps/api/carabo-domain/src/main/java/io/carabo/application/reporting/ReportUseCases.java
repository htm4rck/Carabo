package io.carabo.application.reporting;

import io.carabo.application.reporting.ReportContracts.MonthlyReport;
import io.carabo.application.reporting.ReportContracts.SalesReport;
import io.carabo.application.reporting.ports.ReportReader;
import io.carabo.application.reporting.ports.SalesWorkbook;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.reporting.ReportPeriod;
import java.math.BigDecimal;

public final class ReportUseCases {
    private final ReportReader reader;
    private final SalesWorkbook workbook;

    public ReportUseCases(ReportReader reader, SalesWorkbook workbook) {
        this.reader = reader;
        this.workbook = workbook;
    }

    public SalesReport sales(BusinessScope scope, ReportPeriod period) {
        var rows = reader.byType(scope, period);
        long count = 0;
        BigDecimal base = BigDecimal.ZERO, igv = BigDecimal.ZERO, total = BigDecimal.ZERO;
        for (var row : rows) {
            count += row.count();
            base = base.add(row.taxBase());
            igv = igv.add(row.igvAmount());
            total = total.add(row.total());
        }
        return new SalesReport(scope.businessCode(), period.from().toString(), period.to().toString(),
            count, base, igv, total, rows);
    }

    public MonthlyReport monthly(BusinessScope scope, int year, int month) {
        ReportPeriod period = ReportPeriod.month(year, month);
        var rows = reader.byDay(scope, period);
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (var row : rows) {
            count += row.count();
            total = total.add(row.total());
        }
        return new MonthlyReport(scope.businessCode(), year, month, count, total, rows);
    }

    public byte[] export(BusinessScope scope, ReportPeriod period) {
        return workbook.write(scope, period, consumer -> reader.scanAccepted(scope, period, consumer));
    }
}
