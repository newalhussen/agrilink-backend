package com.agrilink.marketplace;

import com.agrilink.common.AddressDto;
import com.agrilink.common.GeoUtils;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.farmer.FarmerProfile;
import com.agrilink.farmer.FarmerProfileRepository;
import com.agrilink.file.FileController;
import com.agrilink.marketplace.CatalogDtos.ProductResponse;
import com.agrilink.marketplace.ListingDtos.FarmerSummary;
import com.agrilink.marketplace.ListingDtos.ListingResponse;
import com.agrilink.marketplace.ListingDtos.PhotoResponse;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.User;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds {@link ListingResponse}s, loading farmer profiles in one query for a whole page. */
@Component
public class ListingMapper {

    private final FarmerProfileRepository farmerProfiles;
    private final RegionCatalog regions;
    private final String filesBasePath;

    public ListingMapper(FarmerProfileRepository farmerProfiles, RegionCatalog regions,
                         AgriLinkProperties properties) {
        this.farmerProfiles = farmerProfiles;
        this.regions = regions;
        this.filesBasePath = properties.storage().publicBasePath();
    }

    public List<ListingResponse> toResponses(Collection<Listing> listings, Double lat, Double lon) {
        Map<UUID, FarmerProfile> profiles = farmerProfiles
                .findByUserIdIn(listings.stream().map(l -> l.getFarmer().getId()).distinct().toList()).stream()
                .collect(Collectors.toMap(FarmerProfile::getUserId, Function.identity()));
        return listings.stream().map(l -> toResponse(l, profiles.get(l.getFarmer().getId()), lat, lon)).toList();
    }

    public ListingResponse toResponse(Listing l, Double lat, Double lon) {
        FarmerProfile profile = farmerProfiles.findByUserId(l.getFarmer().getId()).orElse(null);
        return toResponse(l, profile, lat, lon);
    }

    private ListingResponse toResponse(Listing l, FarmerProfile profile, Double lat, Double lon) {
        User farmer = l.getFarmer();
        FarmerSummary farmerSummary = new FarmerSummary(farmer.getId(), farmer.getFullName(),
                profile == null ? null : profile.getFarmName(), farmer.getRatingAverage(), farmer.getRatingCount(),
                profile == null ? 0 : profile.getCompletedTrades(), farmer.isVerified());
        Double distance = null;
        if (lat != null && lon != null && l.getAddress().hasCoordinates()) {
            distance = Math.round(GeoUtils.haversineKm(lat, lon, l.getAddress().getLatitude(),
                    l.getAddress().getLongitude()) * 10.0) / 10.0;
        }
        List<PhotoResponse> photos = l.getPhotos().stream()
                .map(p -> new PhotoResponse(p.getId(), p.getFileId(), FileController.urlFor(filesBasePath, p.getFileId()),
                        p.isCover(), p.getSortOrder()))
                .toList();
        return new ListingResponse(l.getId(), l.getStatus(), ProductResponse.from(l.getProduct()), l.getTitle(),
                l.getDescription(), l.getQualityGrade(), l.getQualityNotes(), l.getPackaging(), l.getHarvestDate(),
                l.getAvailableFrom(), l.getAvailableUntil(), l.getUnit(), l.getUnitWeightKg(), l.getQuantityTotal(),
                l.getQuantityAvailable(), l.getMinOrderQuantity(), l.getPricePerUnit(), l.getCurrency(),
                l.isOrganic(), AddressDto.from(l.getAddress(), regions), photos, farmerSummary, distance,
                l.getCreatedAt());
    }
}
