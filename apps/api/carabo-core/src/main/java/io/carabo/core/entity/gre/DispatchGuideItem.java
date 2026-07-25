package io.carabo.core.entity.gre;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "dispatch_guide_item", schema = "carabo")
public class DispatchGuideItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatch_guide_id", nullable = false)
    private DispatchGuide dispatchGuide;

    @Column(name = "line_number", nullable = false)
    private Integer lineNumber;

    @Column(name = "product_code", length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 500)
    private String productName;

    @Column(name = "unit_code", length = 10)
    private String unitCode = "NIU";

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 18, scale = 3)
    private BigDecimal weight;

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public DispatchGuide getDispatchGuide() { return dispatchGuide; }
    public void setDispatchGuide(DispatchGuide dispatchGuide) { this.dispatchGuide = dispatchGuide; }
    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }
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
