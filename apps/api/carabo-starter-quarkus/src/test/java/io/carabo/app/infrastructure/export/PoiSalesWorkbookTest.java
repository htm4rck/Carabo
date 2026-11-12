package io.carabo.app.infrastructure.export;

import io.carabo.application.reporting.ReportContracts.ExportRow;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.reporting.ReportPeriod;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.apache.poi.util.DefaultTempFileCreationStrategy;
import org.apache.poi.util.TempFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PoiSalesWorkbookTest {
    private static final BusinessScope SCOPE = new BusinessScope("TENANT_A");
    private static final ReportPeriod PERIOD = ReportPeriod.month(2026, 1);
    @TempDir Path temporary;

    @Test void exportsBeyondTheWindowPreservingTheExcelContractAndDeletesSpoolFiles() throws Exception {
        TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy(temporary.toFile()));
        try {
            byte[] content = new PoiSalesWorkbook().write(SCOPE, PERIOD, sink -> {
                for (int i = 1; i <= 350; i++) sink.accept(row(i));
                return 350;
            });
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
                assertEquals(2, workbook.getNumberOfSheets());
                var sales = workbook.getSheet("Registro de Ventas");
                assertEquals(350, sales.getLastRowNum());
                assertEquals(11, sales.getRow(0).getLastCellNum());
                assertEquals("RUC/DNI Cliente", sales.getRow(0).getCell(3).getStringCellValue());
                assertEquals("F001-00000001", sales.getRow(1).getCell(2).getStringCellValue());
                assertEquals("F001-00000350", sales.getRow(350).getCell(2).getStringCellValue());
                assertEquals(118.25, sales.getRow(350).getCell(8).getNumericCellValue());
                assertEquals("ACCEPTED", sales.getRow(350).getCell(9).getStringCellValue());
                assertEquals("signed-hash", sales.getRow(350).getCell(10).getStringCellValue());
                var summary = workbook.getSheet("Resumen");
                assertEquals("TENANT_A", summary.getRow(0).getCell(1).getStringCellValue());
                assertEquals("2026-01-01 a 2026-01-31", summary.getRow(1).getCell(1).getStringCellValue());
                assertEquals(350, summary.getRow(2).getCell(1).getNumericCellValue());
            }
            assertNoTemporaryFiles();
        } finally { TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy()); }
    }

    @Test void deletesSpoolFilesWhenReadingTheDatabaseFailsMidExport() throws Exception {
        TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy(temporary.toFile()));
        try {
            var failure = new IllegalStateException("Database interrupted");
            assertSame(failure, assertThrows(IllegalStateException.class, () -> new PoiSalesWorkbook().write(SCOPE, PERIOD, sink -> {
                for (int i = 1; i <= 350; i++) sink.accept(row(i));
                throw failure;
            })));
            assertNoTemporaryFiles();
        } finally { TempFile.setTempFileCreationStrategy(new DefaultTempFileCreationStrategy()); }
    }

    private void assertNoTemporaryFiles() throws Exception {
        try (var files = Files.walk(temporary)) { assertFalse(files.anyMatch(Files::isRegularFile)); }
    }

    private ExportRow row(int number) {
        return new ExportRow(LocalDate.of(2026, 1, 14), DocumentType.INVOICE, "F001", number,
            "20999999991", "Customer", "PEN", new BigDecimal("100.21"), new BigDecimal("18.04"),
            new BigDecimal("118.25"), DocumentStatus.ACCEPTED, "signed-hash");
    }
}
