package io.carabo.app.service;

import io.carabo.core.enums.DocumentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ReportService {

    @Inject
    EntityManager em;

    public SalesReport salesReport(String businessCode, LocalDate from, LocalDate to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createQuery(
            "SELECT d.docType, COUNT(d), COALESCE(SUM(d.taxBase),0), COALESCE(SUM(d.igvAmount),0), COALESCE(SUM(d.total),0) " +
            "FROM Document d WHERE d.businessCode = :bc AND d.status = :st " +
            "AND d.issueDate BETWEEN :from AND :to GROUP BY d.docType")
            .setParameter("bc", businessCode)
            .setParameter("st", DocumentStatus.ACCEPTED)
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();

        var report = new SalesReport();
        report.businessCode = businessCode;
        report.from = from.toString();
        report.to = to.toString();

        for (Object[] row : rows) {
            var line = new SalesReport.TypeSummary();
            line.docType = row[0].toString();
            line.count = ((Number) row[1]).longValue();
            line.taxBase = (BigDecimal) row[2];
            line.igvAmount = (BigDecimal) row[3];
            line.total = (BigDecimal) row[4];
            report.byType.add(line);
            report.totalDocuments += line.count;
            report.totalTaxBase = report.totalTaxBase.add(line.taxBase);
            report.totalIgv = report.totalIgv.add(line.igvAmount);
            report.totalAmount = report.totalAmount.add(line.total);
        }

        return report;
    }

    public MonthlyReport monthlyReport(String businessCode, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        @SuppressWarnings("unchecked")
        List<Object[]> daily = em.createQuery(
            "SELECT d.issueDate, COUNT(d), COALESCE(SUM(d.total),0) " +
            "FROM Document d WHERE d.businessCode = :bc AND d.status = :st " +
            "AND d.issueDate BETWEEN :from AND :to GROUP BY d.issueDate ORDER BY d.issueDate")
            .setParameter("bc", businessCode)
            .setParameter("st", DocumentStatus.ACCEPTED)
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();

        var report = new MonthlyReport();
        report.businessCode = businessCode;
        report.year = year;
        report.month = month;

        for (Object[] row : daily) {
            var day = new MonthlyReport.DaySummary();
            day.date = row[0].toString();
            day.count = ((Number) row[1]).longValue();
            day.total = (BigDecimal) row[2];
            report.days.add(day);
            report.totalDocuments += day.count;
            report.totalAmount = report.totalAmount.add(day.total);
        }

        return report;
    }

    public static class SalesReport {
        public String businessCode;
        public String from;
        public String to;
        public long totalDocuments = 0;
        public BigDecimal totalTaxBase = BigDecimal.ZERO;
        public BigDecimal totalIgv = BigDecimal.ZERO;
        public BigDecimal totalAmount = BigDecimal.ZERO;
        public List<TypeSummary> byType = new java.util.ArrayList<>();

        public static class TypeSummary {
            public String docType;
            public long count;
            public BigDecimal taxBase;
            public BigDecimal igvAmount;
            public BigDecimal total;
        }
    }

    public static class MonthlyReport {
        public String businessCode;
        public int year;
        public int month;
        public long totalDocuments = 0;
        public BigDecimal totalAmount = BigDecimal.ZERO;
        public List<DaySummary> days = new java.util.ArrayList<>();

        public static class DaySummary {
            public String date;
            public long count;
            public BigDecimal total;
        }
    }
}
