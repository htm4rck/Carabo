package io.carabo.core.entity;

import io.carabo.core.enums.SendMode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant", schema = "carabo", uniqueConstraints = @UniqueConstraint(columnNames = "business_code"))
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_code", nullable = false, length = 50)
    private String businessCode;

    @Column(name = "business_name", length = 300)
    private String businessName;

    // Emisor defaults
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

    // Send mode
    @Enumerated(EnumType.STRING)
    @Column(name = "send_mode", nullable = false, length = 20)
    private SendMode sendMode = SendMode.SUNAT_DIRECT;

    // SUNAT credentials
    @Column(name = "sunat_endpoint", length = 500)
    private String sunatEndpoint;

    @Column(name = "sol_user", length = 100)
    private String solUser;

    @Column(name = "sol_password", length = 100)
    private String solPassword;

    // OSE credentials
    @Column(name = "ose_base_url", length = 500)
    private String oseBaseUrl;

    @Column(name = "ose_api_token", length = 500)
    private String oseApiToken;

    // Certificate (path relative to certs volume)
    @Column(name = "certificate_file", length = 200)
    private String certificateFile;

    @Column(name = "certificate_password", length = 200)
    private String certificatePassword;

    @Column(name = "certificate_valid_from")
    private java.time.LocalDate certificateValidFrom;

    @Column(name = "certificate_valid_to")
    private java.time.LocalDate certificateValidTo;

    @Column(name = "certificate_alias", length = 200)
    private String certificateAlias;

    // Environment
    @Column(name = "environment", length = 20)
    private String environment = "BETA"; // BETA, HOMOLOGATION, PRODUCTION

    // Webhook (tenant-level, in addition to per-document callbackUrl)
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 200)
    private String webhookSecret;

    // Series config
    @Column(name = "invoice_series", length = 10)
    private String invoiceSeries = "F001";

    @Column(name = "receipt_series", length = 10)
    private String receiptSeries = "B001";

    @Column(name = "credit_note_series", length = 10)
    private String creditNoteSeries = "FC01";

    @Column(name = "debit_note_series", length = 10)
    private String debitNoteSeries = "FD01";

    // Limits
    @Column(name = "max_documents_per_day")
    private Integer maxDocumentsPerDay = 1000;

    // One-way fingerprint of the API key. The bearer credential is returned only on creation/rotation.
    @Column(name = "api_key_hash", length = 64, unique = true)
    private String apiKeyHash;

    @Column(name = "api_key_rotated_at")
    private Instant apiKeyRotatedAt;

    // Country / tax authority
    @Column(name = "country_code", length = 3)
    private String countryCode = "PE";

    // Status
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
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
    public SendMode getSendMode() { return sendMode; }
    public void setSendMode(SendMode sendMode) { this.sendMode = sendMode; }
    public String getSunatEndpoint() { return sunatEndpoint; }
    public void setSunatEndpoint(String sunatEndpoint) { this.sunatEndpoint = sunatEndpoint; }
    public String getSolUser() { return solUser; }
    public void setSolUser(String solUser) { this.solUser = solUser; }
    public String getSolPassword() { return solPassword; }
    public void setSolPassword(String solPassword) { this.solPassword = solPassword; }
    public String getOseBaseUrl() { return oseBaseUrl; }
    public void setOseBaseUrl(String oseBaseUrl) { this.oseBaseUrl = oseBaseUrl; }
    public String getOseApiToken() { return oseApiToken; }
    public void setOseApiToken(String oseApiToken) { this.oseApiToken = oseApiToken; }
    public String getCertificateFile() { return certificateFile; }
    public void setCertificateFile(String certificateFile) { this.certificateFile = certificateFile; }
    public String getCertificatePassword() { return certificatePassword; }
    public void setCertificatePassword(String certificatePassword) { this.certificatePassword = certificatePassword; }
    public String getInvoiceSeries() { return invoiceSeries; }
    public void setInvoiceSeries(String invoiceSeries) { this.invoiceSeries = invoiceSeries; }
    public String getReceiptSeries() { return receiptSeries; }
    public void setReceiptSeries(String receiptSeries) { this.receiptSeries = receiptSeries; }
    public String getCreditNoteSeries() { return creditNoteSeries; }
    public void setCreditNoteSeries(String creditNoteSeries) { this.creditNoteSeries = creditNoteSeries; }
    public String getDebitNoteSeries() { return debitNoteSeries; }
    public void setDebitNoteSeries(String debitNoteSeries) { this.debitNoteSeries = debitNoteSeries; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public java.time.LocalDate getCertificateValidFrom() { return certificateValidFrom; }
    public void setCertificateValidFrom(java.time.LocalDate certificateValidFrom) { this.certificateValidFrom = certificateValidFrom; }
    public java.time.LocalDate getCertificateValidTo() { return certificateValidTo; }
    public void setCertificateValidTo(java.time.LocalDate certificateValidTo) { this.certificateValidTo = certificateValidTo; }
    public String getCertificateAlias() { return certificateAlias; }
    public void setCertificateAlias(String certificateAlias) { this.certificateAlias = certificateAlias; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public Integer getMaxDocumentsPerDay() { return maxDocumentsPerDay; }
    public void setMaxDocumentsPerDay(Integer maxDocumentsPerDay) { this.maxDocumentsPerDay = maxDocumentsPerDay; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }
    public Instant getApiKeyRotatedAt() { return apiKeyRotatedAt; }
    public void setApiKeyRotatedAt(Instant apiKeyRotatedAt) { this.apiKeyRotatedAt = apiKeyRotatedAt; }
}
