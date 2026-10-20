package io.carabo.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batch_operation", schema = "carabo")
public class BatchOperation {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_code", nullable = false, length = 50)
    private String businessCode;
    @Column(name = "request_key", length = 100)
    private String requestKey;
    @Column(name = "request_hash", length = 64)
    private String requestHash;
    @Column(name = "document_ids", columnDefinition = "TEXT")
    private String documentIds;
    @Column(nullable = false, length = 5)
    private String kind;
    @Column(nullable = false, length = 100)
    private String identifier;
    @Column(length = 100)
    private String ticket;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(name = "reference_date")
    private LocalDate referenceDate;
    @Column(name = "document_count", nullable = false)
    private int documentCount;
    @Column(name = "cdr_xml", columnDefinition = "TEXT")
    private String cdrXml;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash = value; }
    public String getDocumentIds() { return documentIds; }
    public void setDocumentIds(String value) { documentIds = value; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getTicket() { return ticket; }
    public void setTicket(String ticket) { this.ticket = ticket; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDate getReferenceDate() { return referenceDate; }
    public void setReferenceDate(LocalDate referenceDate) { this.referenceDate = referenceDate; }
    public int getDocumentCount() { return documentCount; }
    public void setDocumentCount(int documentCount) { this.documentCount = documentCount; }
    public String getCdrXml() { return cdrXml; }
    public void setCdrXml(String cdrXml) { this.cdrXml = cdrXml; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
