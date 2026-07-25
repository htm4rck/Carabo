package io.carabo.app.service;

import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class DocumentQueryService {

    @Inject
    EntityManager em;

    public QueryResult query(QueryParams params) {
        StringBuilder jpql = new StringBuilder("SELECT d FROM Document d WHERE 1=1");
        StringBuilder countJpql = new StringBuilder("SELECT COUNT(d) FROM Document d WHERE 1=1");

        if (params.businessCode != null) {
            jpql.append(" AND d.businessCode = :bc");
            countJpql.append(" AND d.businessCode = :bc");
        }
        if (params.docType != null) {
            jpql.append(" AND d.docType = :dt");
            countJpql.append(" AND d.docType = :dt");
        }
        if (params.status != null) {
            jpql.append(" AND d.status = :st");
            countJpql.append(" AND d.status = :st");
        }
        if (params.dateFrom != null) {
            jpql.append(" AND d.issueDate >= :df");
            countJpql.append(" AND d.issueDate >= :df");
        }
        if (params.dateTo != null) {
            jpql.append(" AND d.issueDate <= :dt2");
            countJpql.append(" AND d.issueDate <= :dt2");
        }
        if (params.receiverDoc != null) {
            jpql.append(" AND d.receiverDoc = :rd");
            countJpql.append(" AND d.receiverDoc = :rd");
        }
        if (params.series != null) {
            jpql.append(" AND d.series = :sr");
            countJpql.append(" AND d.series = :sr");
        }
        if (params.search != null) {
            jpql.append(" AND (d.receiverName LIKE :q OR d.receiverDoc LIKE :q OR d.externalId LIKE :q)");
            countJpql.append(" AND (d.receiverName LIKE :q OR d.receiverDoc LIKE :q OR d.externalId LIKE :q)");
        }

        jpql.append(" ORDER BY d.createdAt DESC");

        TypedQuery<Document> query = em.createQuery(jpql.toString(), Document.class);
        TypedQuery<Long> countQuery = em.createQuery(countJpql.toString(), Long.class);

        setParams(query, params);
        setParams(countQuery, params);

        long total = countQuery.getSingleResult();
        List<Document> items = query
            .setFirstResult(params.offset)
            .setMaxResults(params.limit)
            .getResultList();

        return new QueryResult(items, total, params.offset, params.limit);
    }

    private void setParams(jakarta.persistence.Query query, QueryParams params) {
        if (params.businessCode != null) query.setParameter("bc", params.businessCode);
        if (params.docType != null) query.setParameter("dt", params.docType);
        if (params.status != null) query.setParameter("st", params.status);
        if (params.dateFrom != null) query.setParameter("df", params.dateFrom);
        if (params.dateTo != null) query.setParameter("dt2", params.dateTo);
        if (params.receiverDoc != null) query.setParameter("rd", params.receiverDoc);
        if (params.series != null) query.setParameter("sr", params.series);
        if (params.search != null) query.setParameter("q", "%" + params.search + "%");
    }

    public static class QueryParams {
        public String businessCode;
        public DocumentType docType;
        public DocumentStatus status;
        public LocalDate dateFrom;
        public LocalDate dateTo;
        public String receiverDoc;
        public String series;
        public String search;
        public int offset = 0;
        public int limit = 50;
    }

    public record QueryResult(List<Document> items, long total, int offset, int limit) {}
}
