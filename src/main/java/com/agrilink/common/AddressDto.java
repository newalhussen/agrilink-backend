package com.agrilink.common;

import com.agrilink.region.RegionCatalog;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Address as exchanged with API clients. {@code regionName} is output-only. */
public record AddressDto(
        UUID regionId,
        String regionName,
        @Size(max = 100) String zone,
        @Size(max = 100) String woreda,
        @Size(max = 100) String town,
        @Size(max = 255) String addressLine,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

    public static AddressDto from(Address a, RegionCatalog regions) {
        if (a == null) {
            return null;
        }
        return new AddressDto(a.getRegionId(), regions.nameOf(a.getRegionId()), a.getZone(), a.getWoreda(),
                a.getTown(), a.getAddressLine(), a.getLatitude(), a.getLongitude());
    }

    /** Converts to an entity value, verifying that the region (if given) exists. */
    public Address toEntity(RegionCatalog regions) {
        if (regionId != null) {
            regions.requireExists(regionId);
        }
        return new Address(regionId, zone, woreda, town, addressLine, latitude, longitude);
    }
}
