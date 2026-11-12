package io.carabo.domain.producer;

import io.carabo.domain.BusinessScope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Producer(UUID id, BusinessScope scope, String code, String name, String description,
                       String credentialHash, Set<String> scopes, String status,
                       Instant createdAt, Instant rotatedAt, Instant lastUsedAt,
                       Instant lastSuccessAt, Instant lastFailureAt, Integer lastHttpStatus,
                       long totalRequests, long failureRequests) {
    public Producer { scopes = Set.copyOf(scopes); }

    public Producer withCredential(String hash, Instant now) {
        return new Producer(id, scope, code, name, description, hash, scopes, "ACTIVE", createdAt,
            now, lastUsedAt, lastSuccessAt, lastFailureAt, lastHttpStatus, totalRequests, failureRequests);
    }

    public Producer withStatus(boolean active) {
        return new Producer(id, scope, code, name, description, credentialHash, scopes,
            active ? "ACTIVE" : "REVOKED", createdAt, rotatedAt, lastUsedAt,
            lastSuccessAt, lastFailureAt, lastHttpStatus, totalRequests, failureRequests);
    }

    public String health(Instant now) {
        if (!"ACTIVE".equals(status)) return "REVOKED";
        if (lastFailureAt != null && (lastSuccessAt == null || lastFailureAt.isAfter(lastSuccessAt))) return "DEGRADED";
        if (lastUsedAt == null) return "PENDING";
        if (lastUsedAt.isBefore(now.minusSeconds(7 * 86400L))) return "IDLE";
        return "HEALTHY";
    }

    @Override public String toString() {
        return "Producer[id=" + id + ", scope=" + scope + ", code=" + code + ", status=" + status + "]";
    }
}
