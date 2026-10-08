package com.agrilink.marketplace;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Catalogue entry (e.g. "Tomato"). Farmers list concrete quantities of it as {@link Listing}s. */
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ProductCategory category;
    @Column(nullable = false, unique = true)
    private String slug;
    @Column(name = "name_en", nullable = false)
    private String nameEn;
    @Column(name = "name_am")
    private String nameAm;
    @Column(name = "name_om")
    private String nameOm;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_unit", nullable = false)
    private Unit defaultUnit;
    private String description;
    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(ProductCategory category, String slug, String nameEn, String nameAm, String nameOm,
                   Unit defaultUnit, String description) {
        this.category = category;
        this.slug = slug;
        this.nameEn = nameEn;
        this.nameAm = nameAm;
        this.nameOm = nameOm;
        this.defaultUnit = defaultUnit;
        this.description = description;
    }

    public void update(ProductCategory category, String nameEn, String nameAm, String nameOm, Unit defaultUnit,
                       String description, boolean active) {
        this.category = category;
        this.nameEn = nameEn;
        this.nameAm = nameAm;
        this.nameOm = nameOm;
        this.defaultUnit = defaultUnit;
        this.description = description;
        this.active = active;
    }

    public ProductCategory getCategory() { return category; }
    public String getSlug() { return slug; }
    public String getNameEn() { return nameEn; }
    public String getNameAm() { return nameAm; }
    public String getNameOm() { return nameOm; }
    public Unit getDefaultUnit() { return defaultUnit; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
