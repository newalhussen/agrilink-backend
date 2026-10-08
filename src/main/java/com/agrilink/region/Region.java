package com.agrilink.region;

import com.agrilink.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "regions")
public class Region extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;
    @Column(name = "name_en", nullable = false)
    private String nameEn;
    @Column(name = "name_am")
    private String nameAm;
    @Column(name = "name_om")
    private String nameOm;
    @Column(nullable = false)
    private boolean active = true;

    protected Region() {
    }

    public String getCode() {
        return code;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameAm() {
        return nameAm;
    }

    public String getNameOm() {
        return nameOm;
    }

    public boolean isActive() {
        return active;
    }
}
