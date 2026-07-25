package io.carabo.app.service;

import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class ExcelExportService {

    @Inject
    EntityManager em;

    public byte[] exportDocuments(String businessCode, LocalDate from, LocalDate to) {
        @SuppressWarnings("unchecked")
        List<Document> docs = em.createQuery(
            "SELECT d FROM Document d WHERE d.businessCode = :bc AND d.status = :st " +
            "AND d.issueDate BETWEEN :from AND :to ORDER BY d.issueDate, d.series, d.number")
            .setParameter("bc", businessCode)
            .setParameter("st", DocumentStatus.ACCEPTED)
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Registro de Ventas");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Headers
            String[] headers = {"Fecha", "Tipo", "Serie-Número", "RUC/DNI Cliente", "Cliente",
                "Moneda", "Base Imponible", "IGV", "Total", "Estado", "Hash"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            int rowNum = 1;
            for (Document doc : docs) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(doc.getIssueDate() != null ? doc.getIssueDate().toString() : "");
                row.createCell(1).setCellValue(doc.getDocType().description());
                row.createCell(2).setCellValue(doc.getFullNumber());
                row.createCell(3).setCellValue(doc.getReceiverDoc());
                row.createCell(4).setCellValue(doc.getReceiverName());
                row.createCell(5).setCellValue(doc.getCurrency());
                row.createCell(6).setCellValue(doc.getTaxBase() != null ? doc.getTaxBase().doubleValue() : 0);
                row.createCell(7).setCellValue(doc.getIgvAmount() != null ? doc.getIgvAmount().doubleValue() : 0);
                row.createCell(8).setCellValue(doc.getTotal() != null ? doc.getTotal().doubleValue() : 0);
                row.createCell(9).setCellValue(doc.getStatus().name());
                row.createCell(10).setCellValue(doc.getHashCode() != null ? doc.getHashCode() : "");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Summary sheet
            Sheet summary = wb.createSheet("Resumen");
            Row sumHeader = summary.createRow(0);
            sumHeader.createCell(0).setCellValue("Empresa");
            sumHeader.createCell(1).setCellValue(businessCode);
            Row sumPeriod = summary.createRow(1);
            sumPeriod.createCell(0).setCellValue("Periodo");
            sumPeriod.createCell(1).setCellValue(from + " a " + to);
            Row sumTotal = summary.createRow(2);
            sumTotal.createCell(0).setCellValue("Total documentos");
            sumTotal.createCell(1).setCellValue(docs.size());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating Excel", e);
        }
    }
}
