package io.carabo.application.reporting.ports;

import io.carabo.application.reporting.ReportContracts.ExportRow;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.reporting.ReportPeriod;
import java.util.function.Consumer;

public interface SalesWorkbook {
    byte[] write(BusinessScope scope, ReportPeriod period, Rows rows);

    @FunctionalInterface
    interface Rows {
        long writeTo(Consumer<ExportRow> consumer);
    }
}
