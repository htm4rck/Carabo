package io.carabo.app.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class CorrelativeService {

    public enum Kind {
        DOCUMENT,
        DISPATCH_GUIDE
    }

    @Inject
    EntityManager em;

    /**
     * Atomically reserves the next number for one tenant, document kind and
     * series. PostgreSQL serializes concurrent updates to the same counter row.
     */
    public int next(String businessCode, Kind kind, String series) {
        Object value = em.createNativeQuery("""
                INSERT INTO carabo.document_sequence
                    (business_code, document_kind, series, last_number, updated_at)
                VALUES (?1, ?2, ?3, 1, NOW())
                ON CONFLICT (business_code, document_kind, series) DO UPDATE
                SET last_number = carabo.document_sequence.last_number + 1,
                    updated_at = NOW()
                RETURNING last_number
                """)
            .setParameter(1, businessCode)
            .setParameter(2, kind.name())
            .setParameter(3, series)
            .getSingleResult();

        return ((Number) value).intValue();
    }
}
