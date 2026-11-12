package io.carabo.application.producer;

import io.carabo.application.producer.ProducerContracts.CreateProducer;
import io.carabo.application.producer.ProducerContracts.DocumentActivity;
import io.carabo.application.producer.ProducerContracts.IssuedCredential;
import io.carabo.application.producer.ProducerContracts.ProducerSummary;
import io.carabo.application.producer.ports.ProducerDirectory;
import io.carabo.application.producer.ports.ProducerKeys;
import io.carabo.application.producer.ports.ProducerStore;
import io.carabo.application.producer.ports.ProducerUnitOfWork;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.RuleViolation;
import io.carabo.domain.producer.Producer;
import io.carabo.domain.producer.ProducerScopes;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ProducerUseCases {
    private final ProducerDirectory directory;
    private final ProducerUnitOfWork work;
    private final ProducerKeys keys;
    private final Clock clock;

    public ProducerUseCases(ProducerDirectory directory, ProducerUnitOfWork work, ProducerKeys keys, Clock clock) {
        this.directory = directory;
        this.work = work;
        this.keys = keys;
        this.clock = clock;
    }

    public List<ProducerSummary> list(BusinessScope scope) {
        Instant now = clock.instant();
        return directory.list(scope).stream().map(row -> {
            requireScope(scope, row.producer());
            return summary(row.producer(), row.documents(), now);
        }).toList();
    }

    public Optional<Producer> authenticate(String rawKey) {
        String hash = keys.hash(rawKey);
        if (hash == null) return Optional.empty();
        return directory.activeByCredentialHash(hash).filter(producer -> "ACTIVE".equals(producer.status()));
    }

    public IssuedCredential create(BusinessScope scope, CreateProducer command) {
        validate(command);
        return work.transaction(scope, store -> {
            requireStore(scope, store);
            String rawKey = keys.generate();
            Instant now = clock.instant();
            var producer = new Producer(null, scope, command.code().trim().toLowerCase(Locale.ROOT), command.name().trim(),
                blankToNull(command.description()), keys.hash(rawKey), command.scopes(), "ACTIVE", now, now,
                null, null, null, null, 0, 0);
            Producer saved = store.save(producer);
            requireScope(scope, saved);
            return new IssuedCredential(summary(saved, new DocumentActivity(0, null), now), rawKey);
        });
    }

    public Optional<IssuedCredential> rotate(BusinessScope scope, UUID id) {
        return work.transaction(scope, store -> {
            requireStore(scope, store);
            return store.findForUpdate(id).map(producer -> {
                requireScope(scope, producer);
                String rawKey = keys.generate();
                Producer saved = store.save(producer.withCredential(keys.hash(rawKey), clock.instant()));
                requireScope(scope, saved);
                return new IssuedCredential(summary(saved, store.documents(id), clock.instant()), rawKey);
            });
        });
    }

    public Optional<ProducerSummary> setActive(BusinessScope scope, UUID id, boolean active) {
        return work.transaction(scope, store -> {
            requireStore(scope, store);
            return store.findForUpdate(id).map(producer -> {
                requireScope(scope, producer);
                Producer saved = store.save(producer.withStatus(active));
                requireScope(scope, saved);
                return summary(saved, store.documents(id), clock.instant());
            });
        });
    }

    public void syncLegacyCredential(BusinessScope scope, String hash, Instant rotatedAt) {
        work.transaction(scope, store -> {
            requireStore(scope, store);
            Producer producer = store.findCodeForUpdate("legacy-tenant").orElseGet(() ->
                new Producer(null, scope, "legacy-tenant", "Credencial heredada",
                    "Compatibilidad temporal; crea una credencial independiente por aplicación productora.",
                    hash, ProducerScopes.ALLOWED, "ACTIVE", clock.instant(), clock.instant(), null, null, null, null, 0, 0));
            requireScope(scope, producer);
            store.save(producer.withCredential(hash, rotatedAt == null ? clock.instant() : rotatedAt));
            return null;
        });
    }

    public void recordActivity(BusinessScope scope, UUID id, int httpStatus) {
        work.independentTransaction(scope, store -> {
            requireStore(scope, store);
            store.recordActivity(id, httpStatus, httpStatus >= 200 && httpStatus < 400, clock.instant());
            return null;
        });
    }

    private ProducerSummary summary(Producer producer, DocumentActivity documents, Instant now) {
        return new ProducerSummary(producer.id(), producer.code(), producer.name(), producer.description(), producer.scopes(),
            producer.status(), producer.health(now), producer.createdAt(), producer.rotatedAt(), producer.lastUsedAt(),
            producer.lastSuccessAt(), producer.lastFailureAt(), producer.lastHttpStatus(), producer.totalRequests(),
            producer.failureRequests(), documents.totalDocuments(), documents.lastDocumentAt());
    }

    private void validate(CreateProducer command) {
        if (command == null) invalid("Solicitud obligatoria");
        if (command.code() == null || !command.code().trim().toLowerCase(Locale.ROOT).matches("[a-z0-9][a-z0-9_-]{2,49}")) {
            invalid("code debe tener 3–50 caracteres: letras minúsculas, números, guion o guion bajo");
        }
        if (command.name() == null || command.name().isBlank() || command.name().trim().length() > 150) {
            invalid("name es obligatorio y admite hasta 150 caracteres");
        }
        if (command.scopes().isEmpty()) invalid("Selecciona al menos un scope");
        if (command.scopes().stream().anyMatch(value -> value == null || !ProducerScopes.ALLOWED.contains(value))) invalid("Existe un scope no permitido");
        if (command.description() != null && command.description().trim().length() > 500) invalid("description admite hasta 500 caracteres");
    }

    private void requireStore(BusinessScope scope, ProducerStore store) {
        if (!scope.equals(store.scope())) invalid("La unidad de trabajo pertenece a otra empresa");
    }

    private void requireScope(BusinessScope scope, Producer producer) {
        if (!scope.equals(producer.scope())) invalid("El productor pertenece a otra empresa");
    }

    private void invalid(String message) { throw new RuleViolation("INVALID_PRODUCER", message, RuleViolation.Kind.INVALID); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
