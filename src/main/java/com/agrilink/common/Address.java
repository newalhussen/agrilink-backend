package com.agrilink.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** Ethiopian location: region / zone / woreda / town plus an optional GPS point. */
@Embeddable
public class Address {

    @Column(name = "region_id")
    private UUID regionId;
    private String zone;
    private String woreda;
    private String town;
    @Column(name = "address_line")
    private String addressLine;
    private Double latitude;
    private Double longitude;

    public Address() {
    }

    public Address(UUID regionId, String zone, String woreda, String town, String addressLine,
                   Double latitude, Double longitude) {
        this.regionId = regionId;
        this.zone = zone;
        this.woreda = woreda;
        this.town = town;
        this.addressLine = addressLine;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Address copy() {
        return new Address(regionId, zone, woreda, town, addressLine, latitude, longitude);
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    public UUID getRegionId() {
        return regionId;
    }

    public String getZone() {
        return zone;
    }

    public String getWoreda() {
        return woreda;
    }

    public String getTown() {
        return town;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
