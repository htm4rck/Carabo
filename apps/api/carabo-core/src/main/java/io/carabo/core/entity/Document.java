package io.carabo.core.entity;

import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.enums.SendMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "document", schema = "carabo",
    uniqueConstraints = @UniqueConstraint(columnNames = {"business_code", "series", "number"}))
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_code", nullable = false, length = 50)
    private String businessCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 5)
    private DocumentType docType;

    @Column(nullable = false, length = 10)
    private String series;

    @Column(nullable = false)
    private Integer number;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 3)
    private String currency = "PEN";

    // Emisor
    @Column(name = "issuer_ruc", nullable = false, length = 11)
    private String issuerRuc;

    @Column(name = "issuer_name", nullable = false, length = 300)
    private String issuerName;

    @Column(name = "issuer_trade", length = 300)
    private String issuerTrade;

    @Column(name = "issuer_address", length = 500)
    private String issuerAddress;

    @Column(name = "issuer_ubigeo", length = 6)
    private String issuerUbigeo;

    // Receptor
    @Column(name = "receiver_doc_type", nullable = false, length = 2)
    private String receiverDocType;

    @Column(name = "receiver_doc", nullable = false, length = 20)
    private String receiverDoc;

    @Column(name = "receiver_name", nullable = false, length = 300)
    private String receiverName;

    @Column(name = "receiver_address", length = 500)
    private String receiverAddress;

    // Totales
    @Column(name = "tax_base", precision = 18, scale = 2)
    private BigDecimal taxBase = BigDecimal.ZERO;

    @Column(name = "igv_amount", precision = 18, scale = 2)
    private BigDecimal igvAmount = BigDecimal.ZERO;

    @Column(name = "isc_amount", precision = 18, scale = 2)
    private BigDecimal iscAmount = BigDecimal.ZERO;

    @Column(name = "other_taxes", precision = 18, scale = 2)
    private BigDecimal otherTaxes = BigDecimal.ZERO;

    @Column(name = "discount_total", precision = 18, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(precision = 18, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    // Estado
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_mode", length = 20)
    private SendMode sendMode = SendMode.SUNAT_DIRECT;

    @Column(name = "hash_code", length = 100)
    private String hashCode;

    @Column(name = "sunat_ticket", length = 100)
    private String sunatTicket;

    @Column(name = "sunat_response", columnDefinition = "TEXT")
    private String sunatResponse;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    // Referencia (NC/ND)
    @Column(name = "ref_doc_type", length = 5)
    private String refDocType;

    @Column(name = "ref_series", length = 10)
    private String refSeries;

    @Column(name = "ref_number")
    private Integer refNumber;

    @Column(name = "ref_reason", length = 500)
    private String refReason;

    @Column(name = "ref_reason_code", length = 2)
    private String refReasonCode;

    // Callback
    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "source_doc_id", length = 100)
    private String sourceDocId;

    // Audit
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // Relations
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentTax> taxes = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL)
    private List<DocumentSendLog> sendLogs = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public DocumentType getDocType() { return docType; }
    public void setDocType(DocumentType docType) { this.docType = docType; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getIssuerRuc() { return issuerRuc; }
    public void setIssuerRuc(String issuerRuc) { this.issuerRuc = issuerRuc; }
    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
    public String getIssuerTrade() { return issuerTrade; }
    public void setIssuerTrade(String issuerTrade) { this.issuerTrade = issuerTrade; }
    public String getIssuerAddress() { return issuerAddress; }
    public void setIssuerAddress(String issuerAddress) { this.issuerAddress = issuerAddress; }
    public String getIssuerUbigeo() { return issuerUbigeo; }
    public void setIssuerUbigeo(String issuerUbigeo) { this.issuerUbigeo = issuerUbigeo; }
    public String getReceiverDocType() { return receiverDocType; }
    public void setReceiverDocType(String receiverDocType) { this.receiverDocType = receiverDocType; }
    public String getReceiverDoc() { return receiverDoc; }
    public void setReceiverDoc(String receiverDoc) { this.receiverDoc = receiverDoc; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }
    public BigDecimal getTaxBase() { return taxBase; }
    public void setTaxBase(BigDecimal taxBase) { this.taxBase = taxBase; }
    public BigDecimal getIgvAmount() { return igvAmount; }
    public void setIgvAmount(BigDecimal igvAmount) { this.igvAmount = igvAmount; }
    public BigDecimal getIscAmount() { return iscAmount; }
    public void setIscAmount(BigDecimal iscAmount) { this.iscAmount = iscAmount; }
    public BigDecimal getOtherTaxes() { return otherTaxes; }
    public void setOtherTaxes(BigDecimal otherTaxes) { this.otherTaxes = otherTaxes; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public SendMode getSendMode() { return sendMode; }
    public void setSendMode(SendMode sendMode) { this.sendMode = sendMode; }
    public String getHashCode() { return hashCode; }
    public void setHashCode(String hashCode) { this.hashCode = hashCode; }
    public String getSunatTicket() { return sunatTicket; }
    public void setSunatTicket(String sunatTicket) { this.sunatTicket = sunatTicket; }
    public String getSunatResponse() { return sunatResponse; }
    public void setSunatResponse(String sunatResponse) { this.sunatResponse = sunatResponse; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getRefDocType() { return refDocType; }
    public void setRefDocType(String refDocType) { this.refDocType = refDocType; }
    public String getRefSeries() { return refSeries; }
    public void setRefSeries(String refSeries) { this.refSeries = refSeries; }
    public Integer getRefNumber() { return refNumber; }
    public void setRefNumber(Integer refNumber) { this.refNumber = refNumber; }
    public String getRefReason() { return refReason; }
    public void setRefReason(String refReason) { this.refReason = refReason; }
    public String getRefReasonCode() { return refReasonCode; }
    public void setRefReasonCode(String refReasonCode) { this.refReasonCode = refReasonCode; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getSourceDocId() { return sourceDocId; }
    public void setSourceDocId(String sourceDocId) { this.sourceDocId = sourceDocId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<DocumentLine> getLines() { return lines; }
    public void setLines(List<DocumentLine> lines) { this.lines = lines; }
    public List<DocumentTax> getTaxes() { return taxes; }
    public void setTaxes(List<DocumentTax> taxes) { this.taxes = taxes; }
    public List<DocumentSendLog> getSendLogs() { return sendLogs; }

    public String getFullNumber() { return series + "-" + String.format("%08d", number); }
}
