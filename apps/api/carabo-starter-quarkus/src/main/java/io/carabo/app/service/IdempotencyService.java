package io.carabo.app.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.Arrays;
import java.util.Objects;

@ApplicationScoped
public class IdempotencyService {

    @Inject
    EntityManager em;

    /**
     * Holds transaction-scoped advisory locks for every supplied identifier.
     * Sorting avoids deadlocks when requests provide both source and external IDs.
     */
    public void lock(String namespace, String businessCode, String... identifiers) {
        Arrays.stream(identifiers)
            .map(IdempotencyService::normalize)
            .filter(Objects::nonNull)
            .map(value -> namespace + ":" + businessCode + ":" + value)
            .distinct()
            .sorted()
            .forEach(this::lockKey);
    }

    public static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void lockKey(String key) {
        em.createNativeQuery(
                "SELECT pg_advisory_xact_lock(hashtextextended(CAST(?1 AS text), 0))")
            .setParameter(1, key)
            .getSingleResult();
    }
}
