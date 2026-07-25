package io.carabo.core.entity.gre;

import io.carabo.core.enums.DocumentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dispatch_guide", schema = "carabo",
    uniqueConstraints = @UniqueConstraint(columnNames = {"business_code", "series", "number"}))
public class DispatchGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_code", nullable = false, length = 50)
    private String businessCode;

    @Column(nullable = false, length = 10)
    private String series; // T001, V001

    @Column(nullable = false)
    private Integer number;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    // Issuer
    @Column(name = "issuer_ruc", nullable = false, length = 11)
    private String issuerRuc;

    @Column(name = "issuer_name", nullable = false, length = 300)
    private String issuerName;

    // Receiver
    @Column(name = "receiver_doc_type", nullable = false, length = 2)
    private String receiverDocType;

    @Column(name = "receiver_doc", nullable = false, length = 20)
    private String receiverDoc;

    @Column(name = "receiver_name", nullable = false, length = 300)
    private String receiverName;

    // Transport mode: 01=Público, 02=Privado
    @Column(name = "transport_mode", nullable = false, length = 2)
    private String transportMode;

    // Weight
    @Column(name = "gross_weight", precision = 18, scale = 3)
    private BigDecimal grossWeight;

    @Column(name = "weight_unit", length = 5)
    private String weightUnit = "KGM";

    @Column(name = "package_count")
    private Integer packageCount;

    // Origin
    @Column(name = "origin_ubigeo", length = 6)
    private String originUbigeo;

    @Column(name = "origin_address", length = 500)
    private String originAddress;

    // Destination
    @Column(name = "destination_ubigeo", length = 6)
    private String destinationUbigeo;

    @Column(name = "destination_address", length = 500)
    private String destinationAddress;

    // Carrier (transport mode 01 - público)
    @Column(name = "carrier_ruc", length = 11)
    private String carrierRuc;

    @Column(name = "carrier_name", length = 300)
    private String carrierName;

    // Driver (transport mode 02 - privado)
    @Column(name = "driver_doc_type", length = 2)
    private String driverDocType;

    @Column(name = "driver_doc", length = 20)
    private String driverDoc;

    @Column(name = "driver_name", length = 300)
    private String driverName;

    @Column(name = "driver_license", length = 20)
    private String driverLicense;

    // Vehicle (transport mode 02 - privado)
    @Column(name = "vehicle_plate", length = 10)
    private String vehiclePlate;

    // Related document
    @Column(name = "related_doc_type", length = 5)
    private String relatedDocType;

    @Column(name = "related_doc_number", length = 30)
    private String relatedDocNumber;

    // Transfer reason code (SUNAT catalog 20)
    @Column(name = "transfer_reason_code", length = 2)
    private String transferReasonCode;

    @Column(name = "transfer_reason_desc", length = 200)
    private String transferReasonDesc;

    // Status & SUNAT
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "hash_code", length = 100)
    private String hashCode;

    @Column(name = "sunat_response", columnDefinition = "TEXT")
    private String sunatResponse;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "dispatchGuide", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DispatchGuideItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    public String getFullNumber() { return series + "-" + String.format("%08d", number); }

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }
    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }
    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public String getIssuerRuc() { return issuerRuc; }
    public void setIssuerRuc(String issuerRuc) { this.issuerRuc = issuerRuc; }
    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
    public String getReceiverDocType() { return receiverDocType; }
    public void setReceiverDocType(String receiverDocType) { this.receiverDocType = receiverDocType; }
    public String getReceiverDoc() { return receiverDoc; }
    public void setReceiverDoc(String receiverDoc) { this.receiverDoc = receiverDoc; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public BigDecimal getGrossWeight() { return grossWeight; }
    public void setGrossWeight(BigDecimal grossWeight) { this.grossWeight = grossWeight; }
    public String getWeightUnit() { return weightUnit; }
    public void setWeightUnit(String weightUnit) { this.weightUnit = weightUnit; }
    public Integer getPackageCount() { return packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount; }
    public String getOriginUbigeo() { return originUbigeo; }
    public void setOriginUbigeo(String originUbigeo) { this.originUbigeo = originUbigeo; }
    public String getOriginAddress() { return originAddress; }
    public void setOriginAddress(String originAddress) { this.originAddress = originAddress; }
    public String getDestinationUbigeo() { return destinationUbigeo; }
    public void setDestinationUbigeo(String destinationUbigeo) { this.destinationUbigeo = destinationUbigeo; }
    public String getDestinationAddress() { return destinationAddress; }
    public void setDestinationAddress(String destinationAddress) { this.destinationAddress = destinationAddress; }
    public String getCarrierRuc() { return carrierRuc; }
    public void setCarrierRuc(String carrierRuc) { this.carrierRuc = carrierRuc; }
    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getDriverDocType() { return driverDocType; }
    public void setDriverDocType(String driverDocType) { this.driverDocType = driverDocType; }
    public String getDriverDoc() { return driverDoc; }
    public void setDriverDoc(String driverDoc) { this.driverDoc = driverDoc; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public String getDriverLicense() { return driverLicense; }
    public void setDriverLicense(String driverLicense) { this.driverLicense = driverLicense; }
    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }
    public String getRelatedDocType() { return relatedDocType; }
    public void setRelatedDocType(String relatedDocType) { this.relatedDocType = relatedDocType; }
    public String getRelatedDocNumber() { return relatedDocNumber; }
    public void setRelatedDocNumber(String relatedDocNumber) { this.relatedDocNumber = relatedDocNumber; }
    public String getTransferReasonCode() { return transferReasonCode; }
    public void setTransferReasonCode(String transferReasonCode) { this.transferReasonCode = transferReasonCode; }
    public String getTransferReasonDesc() { return transferReasonDesc; }
    public void setTransferReasonDesc(String transferReasonDesc) { this.transferReasonDesc = transferReasonDesc; }
    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }
    public String getHashCode() { return hashCode; }
    public void setHashCode(String hashCode) { this.hashCode = hashCode; }
    public String getSunatResponse() { return sunatResponse; }
    public void setSunatResponse(String sunatResponse) { this.sunatResponse = sunatResponse; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<DispatchGuideItem> getItems() { return items; }
    public void setItems(List<DispatchGuideItem> items) { this.items = items; }
}
