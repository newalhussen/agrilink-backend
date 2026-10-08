package com.agrilink.marketplace;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_categories")
public class ProductCategory extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String slug;
    @Column(name = "name_en", nullable = false)
    private String nameEn;
    @Column(name = "name_am")
    private String nameAm;
    @Column(name = "name_om")
    private String nameOm;
    private String icon;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean active = true;

    protected ProductCategory() {
    }

    public ProductCategory(String slug, String nameEn, String nameAm, String nameOm, String icon, int sortOrder) {
        this.slug = slug;
        this.nameEn = nameEn;
        this.nameAm = nameAm;
        this.nameOm = nameOm;
        this.icon = icon;
        this.sortOrder = sortOrder;
    }

    public void update(String nameEn, String nameAm, String nameOm, String icon, int sortOrder, boolean active) {
        this.nameEn = nameEn;
        this.nameAm = nameAm;
        this.nameOm = nameOm;
        this.icon = icon;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public String getSlug() { return slug; }
    public String getNameEn() { return nameEn; }
    public String getNameAm() { return nameAm; }
    public String getNameOm() { return nameOm; }
    public String getIcon() { return icon; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}
