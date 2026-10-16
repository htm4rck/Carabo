package io.carabo.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "async_message_audit", schema = "carabo")
public class AsyncMessageAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_code", nullable = false, length = 50)
    private String businessCode;
    @Column(name = "external_id", length = 100)
    private String externalId;
    @Column(name = "producer_id")
    private UUID producerId;
    @Column(name = "producer_code", length = 50)
    private String producerCode;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    @Column(name = "delivery_count", nullable = false)
    private int deliveryCount;
    @Column(name = "replay_count", nullable = false)
    private int replayCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String value) { businessCode = value; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String value) { externalId = value; }
    public UUID getProducerId() { return producerId; }
    public void setProducerId(UUID value) { producerId = value; }
    public String getProducerCode() { return producerCode; }
    public void setProducerCode(String value) { producerCode = value; }
    public String getPayload() { return payload; }
    public void setPayload(String value) { payload = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public int getDeliveryCount() { return deliveryCount; }
    public void setDeliveryCount(int value) { deliveryCount = value; }
    public int getReplayCount() { return replayCount; }
    public void setReplayCount(int value) { replayCount = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
