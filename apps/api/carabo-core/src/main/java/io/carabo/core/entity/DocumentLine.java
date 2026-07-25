package io.carabo.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "document_line", schema = "carabo")
public class DocumentLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

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

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "gross_price", precision = 18, scale = 6)
    private BigDecimal grossPrice;

    @Column(precision = 18, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax_base", nullable = false, precision = 18, scale = 2)
    private BigDecimal taxBase;

    @Column(name = "igv_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal igvAmount;

    @Column(name = "igv_rate", precision = 5, scale = 2)
    private BigDecimal igvRate = new BigDecimal("18.00");

    @Column(name = "affectation_code", length = 5)
    private String affectationCode = "10";

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
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
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getGrossPrice() { return grossPrice; }
    public void setGrossPrice(BigDecimal grossPrice) { this.grossPrice = grossPrice; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getTaxBase() { return taxBase; }
    public void setTaxBase(BigDecimal taxBase) { this.taxBase = taxBase; }
    public BigDecimal getIgvAmount() { return igvAmount; }
    public void setIgvAmount(BigDecimal igvAmount) { this.igvAmount = igvAmount; }
    public BigDecimal getIgvRate() { return igvRate; }
    public void setIgvRate(BigDecimal igvRate) { this.igvRate = igvRate; }
    public String getAffectationCode() { return affectationCode; }
    public void setAffectationCode(String affectationCode) { this.affectationCode = affectationCode; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
