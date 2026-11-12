package io.carabo.core.dto;

import java.util.UUID;

public class EmitDocumentResponse {

    private UUID id;
    private String fullNumber;
    private String status;
    private String hashCode;
    private String sunatResponse;
    private String externalId;

    public static EmitDocumentResponse of(UUID id, String fullNumber, String status, String hashCode, String sunatResponse, String externalId) {
        var r = new EmitDocumentResponse();
        r.id = id;
        r.fullNumber = fullNumber;
        r.status = status;
        r.hashCode = hashCode;
        r.sunatResponse = sunatResponse;
        r.externalId = externalId;
        return r;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFullNumber() { return fullNumber; }
    public void setFullNumber(String fullNumber) { this.fullNumber = fullNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHashCode() { return hashCode; }
    public void setHashCode(String hashCode) { this.hashCode = hashCode; }
    public String getSunatResponse() { return sunatResponse; }
    public void setSunatResponse(String sunatResponse) { this.sunatResponse = sunatResponse; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
}
