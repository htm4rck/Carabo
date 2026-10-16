package io.carabo.app.service;

import io.carabo.app.security.ApiKeyService;
import io.carabo.core.entity.ProducerIntegration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ProducerIntegrationService {
    public static final Set<String> ALLOWED_SCOPES = Set.of("DOCUMENT_INGEST", "DOCUMENT_READ", "DOCUMENT_EMIT");

    @Inject EntityManager em;
    @Inject ApiKeyService apiKeyService;

    public Optional<ProducerIntegration> findByApiKey(String rawKey) {
        String hash = apiKeyService.hash(rawKey);
        if (hash == null) return Optional.empty();
        return em.createQuery("SELECT p FROM ProducerIntegration p WHERE p.apiKeyHash = :hash AND p.status = 'ACTIVE'", ProducerIntegration.class)
            .setParameter("hash", hash).getResultStream().findFirst();
    }

    public Optional<ProducerIntegration> find(UUID id, String businessCode) {
        return em.createQuery("SELECT p FROM ProducerIntegration p WHERE p.id = :id AND p.businessCode = :bc", ProducerIntegration.class)
            .setParameter("id", id).setParameter("bc", businessCode).getResultStream().findFirst();
    }

    public List<ProducerIntegration> list(String businessCode) {
        return em.createQuery("SELECT p FROM ProducerIntegration p WHERE p.businessCode = :bc ORDER BY p.status, p.name", ProducerIntegration.class)
            .setParameter("bc", businessCode).getResultList();
    }

    @Transactional
    public Created create(String businessCode, String code, String name, String description, Set<String> requestedScopes) {
        String apiKey = apiKeyService.generate();
        ProducerIntegration producer = new ProducerIntegration();
        producer.setBusinessCode(businessCode);
        producer.setCode(normalizeCode(code));
        producer.setName(name.trim());
        producer.setDescription(trimToNull(description));
        producer.setScopes(serializeScopes(requestedScopes));
        producer.setStatus("ACTIVE");
        producer.setApiKeyHash(apiKeyService.hash(apiKey));
        producer.setRotatedAt(Instant.now());
        em.persist(producer);
        em.flush();
        return new Created(producer, apiKey);
    }

    @Transactional
    public Optional<Created> rotate(UUID id, String businessCode) {
        Optional<ProducerIntegration> found = find(id, businessCode);
        if (found.isEmpty()) return Optional.empty();
        String apiKey = apiKeyService.generate();
        ProducerIntegration producer = found.get();
        producer.setApiKeyHash(apiKeyService.hash(apiKey));
        producer.setRotatedAt(Instant.now());
        producer.setStatus("ACTIVE");
        em.merge(producer);
        return Optional.of(new Created(producer, apiKey));
    }

    @Transactional
    public void syncLegacyCredential(String businessCode, String apiKeyHash, Instant rotatedAt) {
        var existing = em.createQuery(
                "SELECT p FROM ProducerIntegration p WHERE p.businessCode = :bc AND p.code = 'legacy-tenant'", ProducerIntegration.class)
            .setParameter("bc", businessCode).getResultStream().findFirst();
        ProducerIntegration producer = existing.orElseGet(ProducerIntegration::new);
        if (existing.isEmpty()) {
            producer.setBusinessCode(businessCode);
            producer.setCode("legacy-tenant");
            producer.setName("Credencial heredada");
            producer.setDescription("Compatibilidad temporal; crea una credencial independiente por aplicación productora.");
            producer.setScopes("DOCUMENT_EMIT,DOCUMENT_INGEST,DOCUMENT_READ");
        }
        producer.setApiKeyHash(apiKeyHash);
        producer.setRotatedAt(rotatedAt != null ? rotatedAt : Instant.now());
        producer.setStatus("ACTIVE");
        if (existing.isEmpty()) em.persist(producer); else em.merge(producer);
    }

    @Transactional
    public Optional<ProducerIntegration> setActive(UUID id, String businessCode, boolean active) {
        Optional<ProducerIntegration> found = find(id, businessCode);
        if (found.isEmpty()) return Optional.empty();
        found.get().setStatus(active ? "ACTIVE" : "REVOKED");
        return Optional.of(em.merge(found.get()));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordActivity(UUID id, int httpStatus) {
        ProducerIntegration producer = em.find(ProducerIntegration.class, id);
        if (producer == null) return;
        Instant now = Instant.now();
        producer.setLastUsedAt(now);
        producer.setLastHttpStatus(httpStatus);
        producer.setTotalRequests(producer.getTotalRequests() + 1);
        if (httpStatus >= 200 && httpStatus < 400) {
            producer.setLastSuccessAt(now);
        } else {
            producer.setLastFailureAt(now);
            producer.setFailureRequests(producer.getFailureRequests() + 1);
        }
        em.merge(producer);
    }

    public Set<String> scopes(ProducerIntegration producer) {
        if (producer.getScopes() == null || producer.getScopes().isBlank()) return Set.of();
        var result = new LinkedHashSet<String>();
        Arrays.stream(producer.getScopes().split(","))
            .map(String::trim).map(value -> value.toUpperCase(Locale.ROOT))
            .filter(ALLOWED_SCOPES::contains).forEach(result::add);
        return Set.copyOf(result);
    }

    public long documentCount(ProducerIntegration producer) {
        return em.createQuery("SELECT COUNT(d) FROM Document d WHERE d.businessCode = :bc AND d.producerId = :id", Long.class)
            .setParameter("bc", producer.getBusinessCode()).setParameter("id", producer.getId()).getSingleResult();
    }

    public Instant lastDocumentAt(ProducerIntegration producer) {
        return em.createQuery("SELECT MAX(d.createdAt) FROM Document d WHERE d.businessCode = :bc AND d.producerId = :id", Instant.class)
            .setParameter("bc", producer.getBusinessCode()).setParameter("id", producer.getId()).getSingleResult();
    }

    public static String validate(String code, String name, Set<String> scopes) {
        if (code == null || !code.trim().toLowerCase(Locale.ROOT).matches("[a-z0-9][a-z0-9_-]{2,49}")) {
            return "code debe tener 3–50 caracteres: letras minúsculas, números, guion o guion bajo";
        }
        if (name == null || name.isBlank() || name.trim().length() > 150) return "name es obligatorio y admite hasta 150 caracteres";
        if (scopes == null || scopes.isEmpty()) return "Selecciona al menos un scope";
        if (!ALLOWED_SCOPES.containsAll(scopes)) return "Existe un scope no permitido";
        return null;
    }

    private String serializeScopes(Set<String> scopes) {
        return scopes.stream().map(value -> value.toUpperCase(Locale.ROOT)).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }
    private String normalizeCode(String code) { return code.trim().toLowerCase(Locale.ROOT); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record Created(ProducerIntegration producer, String apiKey) {}
}
