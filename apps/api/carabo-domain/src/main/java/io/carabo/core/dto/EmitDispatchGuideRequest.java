package io.carabo.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class EmitDispatchGuideRequest {

    private String businessCode;
    private String series;
    private LocalDate issueDate;
    private LocalDate startDate;

    private Receiver receiver;
    private String transportMode; // 01=Público, 02=Privado
    private BigDecimal grossWeight;
    private String weightUnit;
    private Integer packageCount;

    private Address origin;
    private Address destination;

    private Carrier carrier;   // mode 01
    private Driver driver;     // mode 02
    private String vehiclePlate; // mode 02

    private String transferReasonCode;
    private String transferReasonDesc;
    private RelatedDoc relatedDoc;

    private List<Item> items;
    private String callbackUrl;
    private String externalId;

    public static class Receiver {
        private String docType;
        private String doc;
        private String name;
        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getDoc() { return doc; }
        public void setDoc(String doc) { this.doc = doc; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class Address {
        private String ubigeo;
        private String address;
        public String getUbigeo() { return ubigeo; }
        public void setUbigeo(String ubigeo) { this.ubigeo = ubigeo; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    public static class Carrier {
        private String ruc;
        private String name;
        public String getRuc() { return ruc; }
        public void setRuc(String ruc) { this.ruc = ruc; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class Driver {
        private String docType;
        private String doc;
        private String name;
        private String license;
        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getDoc() { return doc; }
        public void setDoc(String doc) { this.doc = doc; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLicense() { return license; }
        public void setLicense(String license) { this.license = license; }
    }

    public static class RelatedDoc {
        private String docType;
        private String number;
        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
    }

    public static class Item {
        private String productCode;
        private String productName;
        private String unitCode;
        private BigDecimal quantity;
        private BigDecimal weight;
        public String getProductCode() { return productCode; }
        public void setProductCode(String productCode) { this.productCode = productCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getUnitCode() { return unitCode; }
        public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getWeight() { return weight; }
        public void setWeight(BigDecimal weight) { this.weight = weight; }
    }

    // Root getters/setters
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Receiver getReceiver() { return receiver; }
    public void setReceiver(Receiver receiver) { this.receiver = receiver; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public BigDecimal getGrossWeight() { return grossWeight; }
    public void setGrossWeight(BigDecimal grossWeight) { this.grossWeight = grossWeight; }
    public String getWeightUnit() { return weightUnit; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }
    public Integer getPackageCount() { return packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount; }
    public Address getOrigin() { return origin; }
    public void setOrigin(Address origin) { this.origin = origin; }
    public Address getDestination() { return destination; }
    public void setDestination(Address destination) { this.destination = destination; }
    public Carrier getCarrier() { return carrier; }
    public void setCarrier(Carrier carrier) { this.carrier = carrier; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }
    public String getTransferReasonCode() { return transferReasonCode; }
    public void setTransferReasonCode(String transferReasonCode) { this.transferReasonCode = transferReasonCode; }
    public String getTransferReasonDesc() { return transferReasonDesc; }
    public void setTransferReasonDesc(String transferReasonDesc) { this.transferReasonDesc = transferReasonDesc; }
    public RelatedDoc getRelatedDoc() { return relatedDoc; }
    public void setRelatedDoc(RelatedDoc relatedDoc) { this.relatedDoc = relatedDoc; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
}
