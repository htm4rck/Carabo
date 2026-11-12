package io.carabo.application.producer;

import io.carabo.application.producer.ProducerContracts.CreateProducer;
import io.carabo.application.producer.ProducerContracts.DocumentActivity;
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
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProducerUseCasesTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private final BusinessScope a = new BusinessScope("TENANT_A");
    private final BusinessScope b = new BusinessScope("TENANT_B");
    private final Memory memory = new Memory();
    private final Keys keys = new Keys();
    private final ProducerUseCases useCases = new ProducerUseCases(memory, memory, keys, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void createsTheCanonicalIdentityAndStoresOnlyTheFingerprint() {
        var issued = useCases.create(a, request());
        Producer stored = memory.data.get(issued.producer().id());
        assertEquals(a, stored.scope());
        assertEquals("erp-main", stored.code());
        assertEquals("ERP principal", stored.name());
        assertEquals("Descripción", stored.description());
        assertEquals("hash:" + issued.apiKey(), stored.credentialHash());
        assertFalse(stored.toString().contains(stored.credentialHash()));
        assertFalse(issued.toString().contains(issued.apiKey()));
        assertEquals("PENDING", issued.producer().health());
        assertEquals(0, issued.producer().totalDocuments());
    }

    @Test void validatesAllEntryPointsBeforeIssuingCredentials() {
        var bad = List.of(new CreateProducer("x", "ERP", null, Set.of("DOCUMENT_READ")),
            new CreateProducer("erp-main", " ", null, Set.of("DOCUMENT_READ")),
            new CreateProducer("erp-main", "ERP", null, Set.of()),
            new CreateProducer("erp-main", "ERP", null, Set.of("ROOT")),
            new CreateProducer("erp-main", "ERP", null, Collections.singleton(null)));
        for (var request : bad) assertThrows(RuleViolation.class, () -> useCases.create(a, request));
        assertThrows(RuleViolation.class, () -> useCases.create(a, null));
        assertEquals(0, keys.generated);
        assertEquals(0, memory.transactions);
    }

    @Test void rotatesAndRevokesOnlyWithinTheAuthorizedCompany() {
        var original = useCases.create(a, request());
        UUID id = original.producer().id();
        assertTrue(useCases.rotate(b, id).isEmpty());
        assertTrue(useCases.setActive(b, id, false).isEmpty());
        assertTrue(useCases.authenticate(original.apiKey()).isPresent());
        var rotated = useCases.rotate(a, id).orElseThrow();
        assertNotEquals(original.apiKey(), rotated.apiKey());
        assertTrue(useCases.authenticate(original.apiKey()).isEmpty());
        assertEquals(id, useCases.authenticate(rotated.apiKey()).orElseThrow().id());
        useCases.setActive(a, id, false);
        assertTrue(useCases.authenticate(rotated.apiKey()).isEmpty());
        assertEquals("REVOKED", useCases.list(a).getFirst().health());
        assertTrue(useCases.list(b).isEmpty());
    }

    @Test void doesNotReturnAKeyWhenTheTransactionFails() {
        memory.failCommit = true;
        assertThrows(IllegalStateException.class, () -> useCases.create(a, request()));
        assertTrue(memory.data.isEmpty());
        assertTrue(useCases.authenticate("key-1").isEmpty());
    }

    @Test void rollsBackARotationWithoutRevokingTheWorkingCredential() {
        var original = useCases.create(a, request());
        memory.failCommit = true;
        assertThrows(IllegalStateException.class, () -> useCases.rotate(a, original.producer().id()));
        assertTrue(useCases.authenticate(original.apiKey()).isPresent());
        assertTrue(useCases.authenticate("key-2").isEmpty());
    }

    @Test void synchronizesTheTenantCredentialWithoutDuplicatingItsIdentity() {
        useCases.syncLegacyCredential(a, "hash:first", NOW.minusSeconds(10));
        Producer first = memory.data.values().iterator().next();
        useCases.syncLegacyCredential(a, "hash:second", NOW);
        assertEquals(1, memory.data.size());
        Producer second = memory.data.get(first.id());
        assertEquals("legacy-tenant", second.code());
        assertEquals(ProducerScopes.ALLOWED, second.scopes());
        assertEquals("hash:second", second.credentialHash());
        assertEquals(NOW, second.rotatedAt());
    }

    @Test void recordsActivityInTheScopedIndependentUnitOfWork() {
        var issued = useCases.create(a, request());
        useCases.recordActivity(a, issued.producer().id(), 503);
        assertEquals(1, memory.independentTransactions);
        assertEquals(a, memory.activityScope);
        assertEquals(503, memory.activityStatus);
        assertFalse(memory.activitySuccessful);
        assertEquals(NOW, memory.activityAt);
        useCases.recordActivity(a, issued.producer().id(), 302);
        assertTrue(memory.activitySuccessful);
    }

    @Test void rejectsAUnitOfWorkThatChangesTheCompany() {
        memory.forceScope = b;
        assertThrows(RuleViolation.class, () -> useCases.create(a, request()));
        assertTrue(memory.data.isEmpty());
        assertEquals(0, keys.generated);
    }

    @Test void rejectsDirectoryRowsBelongingToAnotherCompany() {
        useCases.create(a, request());
        memory.ignoreReadScope = true;
        assertThrows(RuleViolation.class, () -> useCases.list(b));
    }

    @Test void scopesAreCanonicalAndNeverPromoteUnknownPermissions() {
        assertEquals(Set.of("DOCUMENT_READ"), ProducerScopes.parse(" document_read,ROOT,DOCUMENT_READ"));
        assertEquals("DOCUMENT_EMIT,DOCUMENT_READ", ProducerScopes.serialize(Set.of("DOCUMENT_READ", "DOCUMENT_EMIT")));
        assertTrue(ProducerScopes.parse(null).isEmpty());
    }

    @Test void evaluatesHealthWithTheInjectedClock() {
        var issued = useCases.create(a, request());
        Producer base = memory.data.get(issued.producer().id());
        assertEquals("PENDING", base.health(NOW));
        assertEquals("IDLE", activity(base, NOW.minusSeconds(8 * 86400L), null, null).health(NOW));
        assertEquals("HEALTHY", activity(base, NOW, NOW, NOW.minusSeconds(5)).health(NOW));
        assertEquals("DEGRADED", activity(base, NOW, NOW.minusSeconds(5), NOW).health(NOW));
        assertEquals("REVOKED", activity(base, NOW, NOW.minusSeconds(5), NOW).withStatus(false).health(NOW));
    }

    private CreateProducer request() { return new CreateProducer(" ERP-MAIN ", " ERP principal ", " Descripción ", Set.of("DOCUMENT_READ")); }
    private Producer activity(Producer p, Instant used, Instant success, Instant failed) {
        return new Producer(p.id(), p.scope(), p.code(), p.name(), p.description(), p.credentialHash(), p.scopes(), p.status(),
            p.createdAt(), p.rotatedAt(), used, success, failed, null, 0, 0);
    }

    private static final class Keys implements ProducerKeys {
        int generated;
        @Override public String generate() { return "key-" + ++generated; }
        @Override public String hash(String value) { return value == null || value.isBlank() ? null : "hash:" + value; }
    }

    private static final class Memory implements ProducerDirectory, ProducerUnitOfWork {
        Map<UUID, Producer> data = new LinkedHashMap<>();
        boolean failCommit, ignoreReadScope;
        BusinessScope forceScope, activityScope;
        int transactions, independentTransactions, activityStatus;
        boolean activitySuccessful;
        Instant activityAt;

        @Override public List<Overview> list(BusinessScope scope) {
            return data.values().stream().filter(p -> ignoreReadScope || p.scope().equals(scope))
                .map(p -> new Overview(p, new DocumentActivity(0, null))).toList();
        }
        @Override public Optional<Producer> activeByCredentialHash(String hash) {
            return data.values().stream().filter(p -> p.credentialHash().equals(hash) && p.status().equals("ACTIVE")).findFirst();
        }
        @Override public <T> T independentTransaction(BusinessScope scope, Function<ProducerStore, T> operation) {
            independentTransactions++;
            return transaction(scope, operation);
        }
        @Override public <T> T transaction(BusinessScope scope, Function<ProducerStore, T> operation) {
            transactions++;
            var before = new LinkedHashMap<>(data);
            try {
                T result = operation.apply(new Store(forceScope == null ? scope : forceScope));
                if (failCommit) throw new IllegalStateException("Commit failed");
                return result;
            } catch (RuntimeException error) { data = before; throw error; }
        }
        private final class Store implements ProducerStore {
            private final BusinessScope scope;
            Store(BusinessScope scope) { this.scope = scope; }
            @Override public BusinessScope scope() { return scope; }
            @Override public Optional<Producer> findForUpdate(UUID id) { return Optional.ofNullable(data.get(id)).filter(p -> p.scope().equals(scope)); }
            @Override public Optional<Producer> findCodeForUpdate(String code) { return data.values().stream().filter(p -> p.scope().equals(scope) && p.code().equals(code)).findFirst(); }
            @Override public Producer save(Producer p) {
                assertEquals(scope, p.scope());
                UUID id = p.id() == null ? UUID.randomUUID() : p.id();
                var saved = new Producer(id, p.scope(), p.code(), p.name(), p.description(), p.credentialHash(), p.scopes(), p.status(),
                    p.createdAt(), p.rotatedAt(), p.lastUsedAt(), p.lastSuccessAt(), p.lastFailureAt(), p.lastHttpStatus(), p.totalRequests(), p.failureRequests());
                data.put(id, saved);
                return saved;
            }
            @Override public DocumentActivity documents(UUID id) { return new DocumentActivity(0, null); }
            @Override public void recordActivity(UUID id, int status, boolean successful, Instant at) {
                activityScope = scope; activityStatus = status; activitySuccessful = successful; activityAt = at;
            }
        }
    }
}
