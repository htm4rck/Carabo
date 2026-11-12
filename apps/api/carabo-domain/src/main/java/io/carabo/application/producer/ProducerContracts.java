package io.carabo.application.producer;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ProducerContracts {
    private ProducerContracts() {}

    public record CreateProducer(String code, String name, String description, Set<String> scopes) {
        public CreateProducer {
            scopes = scopes == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
        }
    }

    public record DocumentActivity(long totalDocuments, Instant lastDocumentAt) {}

    public record ProducerSummary(UUID id, String code, String name, String description, Set<String> scopes,
                                  String status, String health, Instant createdAt, Instant rotatedAt,
                                  Instant lastUsedAt, Instant lastSuccessAt, Instant lastFailureAt,
                                  Integer lastHttpStatus, long totalRequests, long failureRequests,
                                  long totalDocuments, Instant lastDocumentAt) {
        public ProducerSummary { scopes = Set.copyOf(scopes); }
    }

    public record IssuedCredential(ProducerSummary producer, String apiKey) {
        @Override public String toString() { return "IssuedCredential[producer=" + producer + ", apiKey=[redacted]]"; }
    }
}
