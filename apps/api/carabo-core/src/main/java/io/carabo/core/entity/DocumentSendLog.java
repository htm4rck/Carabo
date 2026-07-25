package io.carabo.core.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_send_log", schema = "carabo")
public class DocumentSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private Integer attempt = 1;

    @Column(name = "send_mode", nullable = false, length = 20)
    private String sendMode;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 100)
    private String ticket;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "response_msg", columnDefinition = "TEXT")
    private String responseMsg;

    @Column(name = "cdr_xml", columnDefinition = "TEXT")
    private String cdrXml;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    void prePersist() { sentAt = LocalDateTime.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public Integer getAttempt() { return attempt; }
    public void setAttempt(Integer attempt) { this.attempt = attempt; }
    public String getSendMode() { return sendMode; }
    public void setSendMode(String sendMode) { this.sendMode = sendMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTicket() { return ticket; }
    public void setTicket(String ticket) { this.ticket = ticket; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getResponseMsg() { return responseMsg; }
    public void setResponseMsg(String responseMsg) { this.responseMsg = responseMsg; }
    public String getCdrXml() { return cdrXml; }
    public void setCdrXml(String cdrXml) { this.cdrXml = cdrXml; }
    public LocalDateTime getSentAt() { return sentAt; }
}
