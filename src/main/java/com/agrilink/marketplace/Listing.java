package com.agrilink.marketplace;

import com.agrilink.common.Address;
import com.agrilink.common.ApiException;
import com.agrilink.common.BaseEntity;
import com.agrilink.common.ErrorCode;
import com.agrilink.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/** A farmer's concrete offer of a product: quantity, price, quality and where it can be collected. */
@Entity
@Table(name = "listings")
public class Listing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id")
    private User farmer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;
    @Column(nullable = false)
    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "quality_grade", nullable = false)
    private QualityGrade qualityGrade = QualityGrade.A;
    @Column(name = "quality_notes")
    private String qualityNotes;
    private String packaging;
    @Column(name = "harvest_date")
    private LocalDate harvestDate;
    @Column(name = "available_from", nullable = false)
    private LocalDate availableFrom;
    @Column(name = "available_until")
    private LocalDate availableUntil;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;
    @Column(name = "unit_weight_kg", nullable = false)
    private BigDecimal unitWeightKg;
    @Column(name = "quantity_total", nullable = false)
    private BigDecimal quantityTotal;
    @Column(name = "quantity_available", nullable = false)
    private BigDecimal quantityAvailable;
    @Column(name = "min_order_quantity", nullable = false)
    private BigDecimal minOrderQuantity = BigDecimal.ONE;
    @Column(name = "price_per_unit", nullable = false)
    private BigDecimal pricePerUnit;
    @Column(nullable = false)
    private String currency = "ETB";
    @Column(nullable = false)
    private boolean organic;
    @Embedded
    private Address address = new Address();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.ACTIVE;
    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cover DESC, sortOrder ASC")
    @BatchSize(size = 50)
    private List<ListingPhoto> photos = new ArrayList<>();

    protected Listing() {
    }

    public Listing(User farmer, Product product) {
        this.farmer = farmer;
        this.product = product;
    }

    /** Takes {@code quantity} out of the available stock for an order. Caller must hold a write lock. */
    public void reserve(BigDecimal quantity) {
        assertOrderable(quantity);
        quantityAvailable = quantityAvailable.subtract(quantity);
        if (quantityAvailable.signum() == 0) {
            status = ListingStatus.SOLD_OUT;
        }
    }

    /** Checks that {@code quantity} could be ordered now, without changing anything. */
    public void assertOrderable(BigDecimal quantity) {
        if (status != ListingStatus.ACTIVE) {
            throw new ApiException(ErrorCode.LISTING_UNAVAILABLE, "'" + title + "' is not available right now");
        }
        if (quantity.compareTo(minOrderQuantity) < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Minimum order for '" + title + "' is " + minOrderQuantity.stripTrailingZeros().toPlainString()
                            + " " + unit.name().toLowerCase());
        }
        if (quantity.compareTo(quantityAvailable) > 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK, "Only "
                    + quantityAvailable.stripTrailingZeros().toPlainString() + " " + unit.name().toLowerCase()
                    + " of '" + title + "' left");
        }
    }

    /** Returns reserved stock (order rejected, cancelled or expired). */
    public void release(BigDecimal quantity) {
        quantityAvailable = quantityAvailable.add(quantity).min(quantityTotal);
        if (status == ListingStatus.SOLD_OUT && quantityAvailable.signum() > 0) {
            status = ListingStatus.ACTIVE;
        }
    }

    public boolean isPubliclyVisible() {
        return status == ListingStatus.ACTIVE || status == ListingStatus.SOLD_OUT;
    }

    public void publish(Instant now) {
        status = quantityAvailable.signum() == 0 ? ListingStatus.SOLD_OUT : ListingStatus.ACTIVE;
        if (publishedAt == null) {
            publishedAt = now;
        }
    }

    /** Sets a new offered quantity, keeping total consistent with what is already reserved. */
    public void restock(BigDecimal newAvailable) {
        BigDecimal delta = newAvailable.subtract(quantityAvailable);
        this.quantityTotal = quantityTotal.add(delta);
        this.quantityAvailable = newAvailable;
        if (status == ListingStatus.SOLD_OUT && newAvailable.signum() > 0) {
            status = ListingStatus.ACTIVE;
        } else if (status == ListingStatus.ACTIVE && newAvailable.signum() == 0) {
            status = ListingStatus.SOLD_OUT;
        }
    }

    public User getFarmer() { return farmer; }
    public Product getProduct() { return product; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public QualityGrade getQualityGrade() { return qualityGrade; }
    public void setQualityGrade(QualityGrade qualityGrade) { this.qualityGrade = qualityGrade; }
    public String getQualityNotes() { return qualityNotes; }
    public void setQualityNotes(String qualityNotes) { this.qualityNotes = qualityNotes; }
    public String getPackaging() { return packaging; }
    public void setPackaging(String packaging) { this.packaging = packaging; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(LocalDate availableFrom) { this.availableFrom = availableFrom; }
    public LocalDate getAvailableUntil() { return availableUntil; }
    public void setAvailableUntil(LocalDate availableUntil) { this.availableUntil = availableUntil; }
    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }
    public BigDecimal getUnitWeightKg() { return unitWeightKg; }
    public void setUnitWeightKg(BigDecimal unitWeightKg) { this.unitWeightKg = unitWeightKg; }
    public BigDecimal getQuantityTotal() { return quantityTotal; }
    public void setQuantityTotal(BigDecimal quantityTotal) { this.quantityTotal = quantityTotal; }
    public BigDecimal getQuantityAvailable() { return quantityAvailable; }
    public void setQuantityAvailable(BigDecimal quantityAvailable) { this.quantityAvailable = quantityAvailable; }
    public BigDecimal getMinOrderQuantity() { return minOrderQuantity; }
    public void setMinOrderQuantity(BigDecimal minOrderQuantity) { this.minOrderQuantity = minOrderQuantity; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public String getCurrency() { return currency; }
    public boolean isOrganic() { return organic; }
    public void setOrganic(boolean organic) { this.organic = organic; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }
    public Instant getPublishedAt() { return publishedAt; }
    public List<ListingPhoto> getPhotos() { return photos; }
}
