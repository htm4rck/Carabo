package io.carabo.app.infrastructure.export;

import io.carabo.application.reporting.ports.SalesWorkbook;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.reporting.ReportPeriod;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

@ApplicationScoped
public class PoiSalesWorkbook implements SalesWorkbook {
    private static final String[] HEADERS = {"Fecha", "Tipo", "Serie-Número", "RUC/DNI Cliente", "Cliente",
        "Moneda", "Base Imponible", "IGV", "Total", "Estado", "Hash"};

    @Override
    public byte[] write(BusinessScope scope, ReportPeriod period, Rows rows) {
        var workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (workbook; var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Registro de Ventas");
            sheet.trackAllColumnsForAutoSizing();
            var style = workbook.createCellStyle();
            var font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(style);
            }
            int[] nextRow = {1};
            long count = rows.writeTo(value -> {
                var row = sheet.createRow(nextRow[0]++);
                row.createCell(0).setCellValue(value.issueDate() == null ? "" : value.issueDate().toString());
                row.createCell(1).setCellValue(value.docType().description());
                row.createCell(2).setCellValue(value.fullNumber());
                row.createCell(3).setCellValue(value.receiverDoc());
                row.createCell(4).setCellValue(value.receiverName());
                row.createCell(5).setCellValue(value.currency());
                row.createCell(6).setCellValue(value.taxBase() == null ? 0 : value.taxBase().doubleValue());
                row.createCell(7).setCellValue(value.igvAmount() == null ? 0 : value.igvAmount().doubleValue());
                row.createCell(8).setCellValue(value.total() == null ? 0 : value.total().doubleValue());
                row.createCell(9).setCellValue(value.status().name());
                row.createCell(10).setCellValue(value.hash() == null ? "" : value.hash());
            });
            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);
            var summary = workbook.createSheet("Resumen");
            var company = summary.createRow(0);
            company.createCell(0).setCellValue("Empresa");
            company.createCell(1).setCellValue(scope.businessCode());
            var dates = summary.createRow(1);
            dates.createCell(0).setCellValue("Periodo");
            dates.createCell(1).setCellValue(period.from() + " a " + period.to());
            var documents = summary.createRow(2);
            documents.createCell(0).setCellValue("Total documentos");
            documents.createCell(1).setCellValue(count);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Error generating Excel", error);
        } finally {
            workbook.dispose();
        }
    }
}
