package io.carabo.app.infrastructure.persistence;

import io.carabo.application.reporting.ReportContracts.DaySummary;
import io.carabo.application.reporting.ReportContracts.ExportRow;
import io.carabo.application.reporting.ReportContracts.TypeSummary;
import io.carabo.application.reporting.ports.ReportReader;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.reporting.ReportPeriod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ApplicationScoped
public class JpaReportReader implements ReportReader {
    @Inject EntityManager em;

    @Override
    public List<TypeSummary> byType(BusinessScope scope, ReportPeriod period) {
        return scoped(em.createQuery(
            "SELECT d.docType, COUNT(d), COALESCE(SUM(d.taxBase),0), COALESCE(SUM(d.igvAmount),0), COALESCE(SUM(d.total),0) " +
            "FROM Document d WHERE d.businessCode = :bc AND d.status = :st " +
            "AND d.issueDate BETWEEN :from AND :to GROUP BY d.docType", Object[].class), scope, period)
            .getResultList().stream().map(row -> new TypeSummary(row[0].toString(), ((Number) row[1]).longValue(),
                (BigDecimal) row[2], (BigDecimal) row[3], (BigDecimal) row[4])).toList();
    }

    @Override
    public List<DaySummary> byDay(BusinessScope scope, ReportPeriod period) {
        return scoped(em.createQuery(
            "SELECT d.issueDate, COUNT(d), COALESCE(SUM(d.total),0) " +
            "FROM Document d WHERE d.businessCode = :bc AND d.status = :st " +
            "AND d.issueDate BETWEEN :from AND :to GROUP BY d.issueDate ORDER BY d.issueDate", Object[].class), scope, period)
            .getResultList().stream().map(row -> new DaySummary(row[0].toString(), ((Number) row[1]).longValue(), (BigDecimal) row[2])).toList();
    }

    @Override
    @Transactional
    public long scanAccepted(BusinessScope scope, ReportPeriod period, Consumer<ExportRow> rows) {
        TypedQuery<Object[]> query = scoped(em.createQuery(
            "SELECT d.issueDate, d.docType, d.series, d.number, d.receiverDoc, d.receiverName, d.currency, " +
            "d.taxBase, d.igvAmount, d.total, d.status, d.hashCode " +
            "FROM Document d WHERE d.businessCode = :bc AND d.status = :st " +
            "AND d.issueDate BETWEEN :from AND :to ORDER BY d.issueDate, d.series, d.number", Object[].class), scope, period);
        query.setHint("org.hibernate.fetchSize", 250);
        long count = 0;
        try (Stream<Object[]> stream = query.getResultStream()) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                Object[] row = iterator.next();
                rows.accept(new ExportRow((LocalDate) row[0], (DocumentType) row[1], (String) row[2],
                    (Integer) row[3], (String) row[4], (String) row[5], (String) row[6],
                    (BigDecimal) row[7], (BigDecimal) row[8], (BigDecimal) row[9],
                    (DocumentStatus) row[10], (String) row[11]));
                count++;
            }
        }
        return count;
    }

    private <T> TypedQuery<T> scoped(TypedQuery<T> query, BusinessScope scope, ReportPeriod period) {
        return query.setParameter("bc", scope.businessCode()).setParameter("st", DocumentStatus.ACCEPTED)
            .setParameter("from", period.from()).setParameter("to", period.to());
    }
}
