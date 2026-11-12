package io.carabo.app.infrastructure.persistence;

import io.carabo.application.producer.ProducerContracts.DocumentActivity;
import io.carabo.application.producer.ports.ProducerDirectory;
import io.carabo.application.producer.ports.ProducerStore;
import io.carabo.application.producer.ports.ProducerUnitOfWork;
import io.carabo.core.entity.ProducerIntegration;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.RuleViolation;
import io.carabo.domain.producer.Producer;
import io.carabo.domain.producer.ProducerScopes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@ApplicationScoped
public class JpaProducerRepository implements ProducerDirectory, ProducerUnitOfWork {
    @Inject EntityManager em;

    @Override
    public List<Overview> list(BusinessScope scope) {
        return em.createQuery(
            "SELECT p, COUNT(d), MAX(d.createdAt) FROM ProducerIntegration p " +
            "LEFT JOIN Document d ON d.producerId = p.id AND d.businessCode = p.businessCode " +
            "WHERE p.businessCode = :bc GROUP BY p ORDER BY p.status, p.name", Object[].class)
            .setParameter("bc", scope.businessCode()).getResultList().stream()
            .map(row -> new Overview(JpaProducerMapping.model((ProducerIntegration) row[0]),
                new DocumentActivity(((Number) row[1]).longValue(), (Instant) row[2]))).toList();
    }

    @Override
    public Optional<Producer> activeByCredentialHash(String hash) {
        return em.createQuery("SELECT p FROM ProducerIntegration p WHERE p.apiKeyHash = :hash AND p.status = 'ACTIVE'", ProducerIntegration.class)
            .setParameter("hash", hash).setMaxResults(1).getResultList().stream().findFirst().map(JpaProducerMapping::model);
    }

    @Override
    @Transactional
    public <T> T transaction(BusinessScope scope, Function<ProducerStore, T> operation) {
        return operation.apply(new Store(scope));
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public <T> T independentTransaction(BusinessScope scope, Function<ProducerStore, T> operation) {
        return operation.apply(new Store(scope));
    }

    private final class Store implements ProducerStore {
        private final BusinessScope scope;
        private Store(BusinessScope scope) { this.scope = scope; }
        @Override public BusinessScope scope() { return scope; }

        @Override
        public Optional<Producer> findForUpdate(UUID id) {
            return entity(id).map(JpaProducerMapping::model);
        }

        private Optional<ProducerIntegration> entity(UUID id) {
            return em.createQuery("SELECT p FROM ProducerIntegration p WHERE p.id = :id AND p.businessCode = :bc", ProducerIntegration.class)
                .setParameter("id", id).setParameter("bc", scope.businessCode()).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1).getResultList().stream().findFirst();
        }

        @Override
        public Optional<Producer> findCodeForUpdate(String code) {
            return em.createQuery("SELECT p FROM ProducerIntegration p WHERE p.businessCode = :bc AND p.code = :code", ProducerIntegration.class)
                .setParameter("bc", scope.businessCode()).setParameter("code", code).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1).getResultList().stream().findFirst().map(JpaProducerMapping::model);
        }

        @Override
        public Producer save(Producer producer) {
            if (!scope.equals(producer.scope())) {
                throw new RuleViolation("PRODUCER_COMPANY_MISMATCH", "El productor pertenece a otra empresa", RuleViolation.Kind.INVALID);
            }
            ProducerIntegration value = producer.id() == null ? new ProducerIntegration()
                : entity(producer.id()).orElseThrow(() -> new RuleViolation("PRODUCER_NOT_FOUND", "Productor no encontrado", RuleViolation.Kind.MISSING));
            value.setBusinessCode(scope.businessCode());
            value.setCode(producer.code());
            value.setName(producer.name());
            value.setDescription(producer.description());
            value.setApiKeyHash(producer.credentialHash());
            value.setScopes(ProducerScopes.serialize(producer.scopes()));
            value.setStatus(producer.status());
            value.setRotatedAt(producer.rotatedAt());
            try {
                if (producer.id() == null) em.persist(value);
                em.flush();
            } catch (PersistenceException error) {
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                        throw new RuleViolation("PRODUCER_EXISTS", "Ya existe un productor con ese código", RuleViolation.Kind.CONFLICT);
                    }
                }
                throw error;
            }
            return JpaProducerMapping.model(value);
        }

        @Override
        public DocumentActivity documents(UUID id) {
            Object[] row = em.createQuery("SELECT COUNT(d), MAX(d.createdAt) FROM Document d WHERE d.businessCode = :bc AND d.producerId = :id", Object[].class)
                .setParameter("bc", scope.businessCode()).setParameter("id", id).getSingleResult();
            return new DocumentActivity(((Number) row[0]).longValue(), (Instant) row[1]);
        }

        @Override
        public void recordActivity(UUID id, int status, boolean successful, Instant at) {
            String result = successful ? "p.lastSuccessAt = :at" : "p.lastFailureAt = :at, p.failureRequests = p.failureRequests + 1";
            em.createQuery("UPDATE ProducerIntegration p SET p.lastUsedAt = :at, p.lastHttpStatus = :status, " +
                "p.totalRequests = p.totalRequests + 1, p.updatedAt = :at, " + result + " WHERE p.id = :id AND p.businessCode = :bc")
                .setParameter("at", at).setParameter("status", status).setParameter("id", id)
                .setParameter("bc", scope.businessCode()).executeUpdate();
        }
    }
}
