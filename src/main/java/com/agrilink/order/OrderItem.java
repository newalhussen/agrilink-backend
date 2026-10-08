package com.agrilink.order;

import com.agrilink.common.BaseEntity;
import com.agrilink.marketplace.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One purchased line. Name, unit and price are snapshotted so later listing edits cannot change history. */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;
    @Column(name = "listing_id", nullable = false)
    private UUID listingId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "product_name", nullable = false)
    private String productName;
    @Column(name = "quality_grade")
    private String qualityGrade;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;
    @Column(nullable = false)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
    @Column(name = "unit_weight_kg", nullable = false)
    private BigDecimal unitWeightKg;
    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    public OrderItem(Order order, UUID listingId, UUID productId, String productName, String qualityGrade, Unit unit,
                     BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitWeightKg, BigDecimal lineTotal) {
        this.order = order;
        this.listingId = listingId;
        this.productId = productId;
        this.productName = productName;
        this.qualityGrade = qualityGrade;
        this.unit = unit;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitWeightKg = unitWeightKg;
        this.lineTotal = lineTotal;
    }

    public BigDecimal weightKg() {
        return quantity.multiply(unitWeightKg);
    }

    public Order getOrder() { return order; }
    public UUID getListingId() { return listingId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getQualityGrade() { return qualityGrade; }
    public Unit getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitWeightKg() { return unitWeightKg; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
