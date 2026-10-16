package io.carabo.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Machine identity for one external document producer. */
@Entity
@Table(name = "producer_integration", schema = "carabo",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"business_code", "code"}),
        @UniqueConstraint(columnNames = "api_key_hash")
    })
public class ProducerIntegration {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_code", nullable = false, length = 50)
    private String businessCode;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    @Column(nullable = false, length = 200)
    private String scopes;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(name = "last_success_at")
    private Instant lastSuccessAt;
    @Column(name = "last_failure_at")
    private Instant lastFailureAt;
    @Column(name = "last_http_status")
    private Integer lastHttpStatus;
    @Column(name = "total_requests", nullable = false)
    private long totalRequests;
    @Column(name = "failure_requests", nullable = false)
    private long failureRequests;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        createdAt = updatedAt = now;
        if (rotatedAt == null) rotatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String value) { businessCode = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String value) { apiKeyHash = value; }
    public String getScopes() { return scopes; }
    public void setScopes(String value) { scopes = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getRotatedAt() { return rotatedAt; }
    public void setRotatedAt(Instant value) { rotatedAt = value; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant value) { lastUsedAt = value; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant value) { lastSuccessAt = value; }
    public Instant getLastFailureAt() { return lastFailureAt; }
    public void setLastFailureAt(Instant value) { lastFailureAt = value; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public void setLastHttpStatus(Integer value) { lastHttpStatus = value; }
    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long value) { totalRequests = value; }
    public long getFailureRequests() { return failureRequests; }
    public void setFailureRequests(long value) { failureRequests = value; }
}
