package io.carabo.core.dto;

import java.util.UUID;

public class DocumentCallback {

    private UUID documentId;
    private String externalId;
    private String fullNumber;
    private String status;
    private String hashCode;
    private String sunatResponseCode;
    private String sunatResponseMsg;

    public static DocumentCallback success(UUID documentId, String externalId, String fullNumber, String hashCode) {
        var cb = new DocumentCallback();
        cb.documentId = documentId;
        cb.externalId = externalId;
        cb.fullNumber = fullNumber;
        cb.status = "ACCEPTED";
        cb.hashCode = hashCode;
        return cb;
    }

    public static DocumentCallback failure(UUID documentId, String externalId, String status, String code, String msg) {
        var cb = new DocumentCallback();
        cb.documentId = documentId;
        cb.externalId = externalId;
        cb.status = status;
        cb.sunatResponseCode = code;
        cb.sunatResponseMsg = msg;
        return cb;
    }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getFullNumber() { return fullNumber; }
    public void setFullNumber(String fullNumber) { this.fullNumber = fullNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHashCode() { return hashCode; }
    public void setHashCode(String hashCode) { this.hashCode = hashCode; }
    public String getSunatResponseCode() { return sunatResponseCode; }
    public void setSunatResponseCode(String sunatResponseCode) { this.sunatResponseCode = sunatResponseCode; }
    public String getSunatResponseMsg() { return sunatResponseMsg; }
    public void setSunatResponseMsg(String sunatResponseMsg) { this.sunatResponseMsg = sunatResponseMsg; }
}
