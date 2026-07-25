package io.carabo.app.service;

import io.carabo.core.entity.Tenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TenantService {

    private static final Logger LOG = Logger.getLogger(TenantService.class);

    @Inject
    EntityManager em;

    private final Map<String, Tenant> cache = new ConcurrentHashMap<>();

    public Tenant resolve(String businessCode) {
        Tenant cached = cache.get(businessCode);
        if (cached != null) return cached;

        try {
            Tenant tenant = (Tenant) em.createQuery(
                "SELECT t FROM Tenant t WHERE t.businessCode = :bc AND t.active = true")
                .setParameter("bc", businessCode)
                .getSingleResult();
            cache.put(businessCode, tenant);
            return tenant;
        } catch (NoResultException e) {
            throw new IllegalArgumentException("Tenant not found or inactive: " + businessCode);
        }
    }

    public Optional<Tenant> findByBusinessCode(String businessCode) {
        try {
            return Optional.of(resolve(businessCode));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void evictAll() {
        cache.clear();
    }

    private final Map<String, Tenant> apiKeyCache = new ConcurrentHashMap<>();

    public Optional<Tenant> findByApiKey(String apiKey) {
        Tenant cached = apiKeyCache.get(apiKey);
        if (cached != null) return Optional.of(cached);
        try {
            Tenant tenant = (Tenant) em.createQuery(
                "SELECT t FROM Tenant t WHERE t.apiKey = :key")
                .setParameter("key", apiKey)
                .getSingleResult();
            apiKeyCache.put(apiKey, tenant);
            return Optional.of(tenant);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public void evict(String businessCode) {
        cache.remove(businessCode);
        apiKeyCache.values().removeIf(t -> t.getBusinessCode().equals(businessCode));
    }
}
