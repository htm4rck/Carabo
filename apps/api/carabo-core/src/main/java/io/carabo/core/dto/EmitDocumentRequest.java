package io.carabo.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class EmitDocumentRequest {

    private String businessCode;
    private String docType;          // 01, 03, 07, 08
    private String series;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String currency;
    private String sendMode;         // SUNAT_DIRECT, OSE

    // Emisor
    private Issuer issuer;
    // Receptor
    private Receiver receiver;
    // Líneas
    private List<Line> lines;
    // Referencia (NC/ND)
    private Reference reference;
    // Callback
    private String callbackUrl;
    private String externalId;
    private String sourceDocId;

    public static class Issuer {
        private String ruc;
        private String name;
        private String tradeName;
        private String address;
        private String ubigeo;

        public String getRuc() { return ruc; }
        public void setRuc(String ruc) { this.ruc = ruc; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getTradeName() { return tradeName; }
        public void setTradeName(String tradeName) { this.tradeName = tradeName; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getUbigeo() { return ubigeo; }
        public void setUbigeo(String ubigeo) { this.ubigeo = ubigeo; }
    }

    public static class Receiver {
        private String docType;      // 6=RUC, 1=DNI, 4=CE
        private String doc;
        private String name;
        private String address;

        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getDoc() { return doc; }
        public void setDoc(String doc) { this.doc = doc; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    public static class Line {
        private String productCode;
        private String productName;
        private String unitCode;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal discount;
        private String affectationCode;

        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getUnitCode() { return unitCode; }
        public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getDiscount() { return discount; }
        public void setDiscount(BigDecimal discount) { this.discount = discount; }
        public String getAffectationCode() { return affectationCode; }
        public void setAffectationCode(String affectationCode) { this.affectationCode = affectationCode; }
    }

    public static class Reference {
        private String docType;
        private String series;
        private Integer number;
        private String reasonCode;
        private String reason;

        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getSeries() { return series; }
        public void setSeries(String series) { this.series = series; }
        public Integer getNumber() { return number; }
        public void setNumber(Integer number) { this.number = number; }
        public String getReasonCode() { return reasonCode; }
        public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getSendMode() { return sendMode; }
    public void setSendMode(String sendMode) { this.sendMode = sendMode; }
    public Issuer getIssuer() { return issuer; }
    public void setIssuer(Issuer issuer) { this.issuer = issuer; }
    public Receiver getReceiver() { return receiver; }
    public void setReceiver(Receiver receiver) { this.receiver = receiver; }
    public List<Line> getLines() { return lines; }
    public void setLines(List<Line> lines) { this.lines = lines; }
    public Reference getReference() { return reference; }
    public void setReference(Reference reference) { this.reference = reference; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getSourceDocId() { return sourceDocId; }
    public void setSourceDocId(String sourceDocId) { this.sourceDocId = sourceDocId; }
}
