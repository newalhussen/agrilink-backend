package com.agrilink.farmer;

import com.agrilink.common.ApiException;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.common.AddressDto;
import com.agrilink.farmer.FarmerDtos.FarmerProfileResponse;
import com.agrilink.farmer.FarmerDtos.PublicFarmerResponse;
import com.agrilink.farmer.FarmerDtos.UpdateFarmerProfileRequest;
import com.agrilink.file.FileController;
import com.agrilink.marketplace.CatalogDtos.ProductResponse;
import com.agrilink.marketplace.Product;
import com.agrilink.marketplace.ProductRepository;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerService {

    private final FarmerProfileRepository profiles;
    private final UserRepository users;
    private final ProductRepository products;
    private final RegionCatalog regions;
    private final String filesBasePath;

    public FarmerService(FarmerProfileRepository profiles, UserRepository users, ProductRepository products,
                         RegionCatalog regions, AgriLinkProperties properties) {
        this.profiles = profiles;
        this.users = users;
        this.products = products;
        this.regions = regions;
        this.filesBasePath = properties.storage().publicBasePath();
    }

    @Transactional(readOnly = true)
    public FarmerProfileResponse getMine(UUID userId) {
        User user = requireFarmer(userId);
        return toResponse(user, profileWithProducts(userId));
    }

    @Transactional
    public FarmerProfileResponse updateMine(UUID userId, UpdateFarmerProfileRequest r) {
        User user = requireFarmer(userId);
        FarmerProfile p = profileWithProducts(userId);
        if (r.farmerType() != null) p.setFarmerType(r.farmerType());
        if (r.farmName() != null) p.setFarmName(r.farmName().trim());
        if (r.memberCount() != null) p.setMemberCount(r.memberCount());
        if (r.address() != null) p.setAddress(r.address().toEntity(regions));
        if (r.landSizeHectares() != null) p.setLandSizeHectares(r.landSizeHectares());
        if (r.irrigated() != null) p.setIrrigated(r.irrigated());
        if (r.expectedMonthlySupplyKg() != null) p.setExpectedMonthlySupplyKg(r.expectedMonthlySupplyKg());
        if (r.bio() != null) p.setBio(r.bio());
        if (r.faydaIdNumber() != null) p.setFaydaIdNumber(r.faydaIdNumber().trim());
        if (r.payoutMethod() != null) p.setPayoutMethod(r.payoutMethod());
        if (r.payoutAccountName() != null) p.setPayoutAccountName(r.payoutAccountName().trim());
        if (r.payoutAccountNumber() != null) p.setPayoutAccountNumber(r.payoutAccountNumber().trim());
        if (r.productIds() != null) {
            List<Product> found = products.findAllWithCategory(r.productIds());
            if (found.size() != r.productIds().size()) {
                throw ApiException.badRequest("One or more products do not exist");
            }
            p.getProducts().clear();
            p.getProducts().addAll(found);
        }
        return toResponse(user, p);
    }

    @Transactional(readOnly = true)
    public PublicFarmerResponse getPublic(UUID farmerUserId) {
        User user = users.findById(farmerUserId).filter(u -> u.getRole() == Role.FARMER)
                .orElseThrow(() -> ApiException.notFound("Farmer"));
        FarmerProfile p = profileWithProducts(farmerUserId);
        AddressDto address = AddressDto.from(p.getAddress(), regions);
        // Only coarse location is public; exact GPS and address line stay private.
        AddressDto coarse = address == null ? null : new AddressDto(address.regionId(), address.regionName(),
                address.zone(), address.woreda(), address.town(), null, null, null);
        return new PublicFarmerResponse(user.getId(), user.getFullName(), p.getFarmName(), p.getFarmerType(),
                p.getMemberCount(), coarse, user.getRatingAverage(), user.getRatingCount(), p.getCompletedTrades(),
                user.isVerified(),
                user.getProfilePhotoFileId() == null ? null
                        : FileController.urlFor(filesBasePath, user.getProfilePhotoFileId()),
                productResponses(p.getProducts()));
    }

    /** Lookup used by other modules (e.g. to snapshot a farmer's payout details). */
    @Transactional(readOnly = true)
    public FarmerProfile requireProfile(UUID userId) {
        return profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Farmer profile"));
    }

    private User requireFarmer(UUID userId) {
        return users.findById(userId).filter(u -> u.getRole() == Role.FARMER)
                .orElseThrow(() -> ApiException.forbidden("Only farmers can use this endpoint"));
    }

    private FarmerProfile profileWithProducts(UUID userId) {
        return profiles.findByUserIdWithProducts(userId)
                .orElseThrow(() -> ApiException.notFound("Farmer profile"));
    }

    private FarmerProfileResponse toResponse(User user, FarmerProfile p) {
        return new FarmerProfileResponse(user.getId(), p.getFarmerType(), p.getFarmName(), p.getMemberCount(),
                AddressDto.from(p.getAddress(), regions), p.getLandSizeHectares(), p.isIrrigated(),
                p.getExpectedMonthlySupplyKg(), p.getBio(), p.getFaydaIdNumber(), p.getPayoutMethod(),
                p.getPayoutAccountName(), p.getPayoutAccountNumber(), p.getCompletedTrades(),
                user.getVerificationStatus(), productResponses(p.getProducts()));
    }

    private List<ProductResponse> productResponses(Set<Product> set) {
        return new HashSet<>(set).stream().map(ProductResponse::from)
                .sorted((a, b) -> a.nameEn().compareToIgnoreCase(b.nameEn())).toList();
    }
}
