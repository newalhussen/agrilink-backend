package com.agrilink.buyer;

import com.agrilink.buyer.BuyerDtos.BuyerProfileResponse;
import com.agrilink.buyer.BuyerDtos.UpdateBuyerProfileRequest;
import com.agrilink.common.AddressDto;
import com.agrilink.common.ApiException;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuyerService {

    private final BuyerProfileRepository profiles;
    private final UserRepository users;
    private final RegionCatalog regions;

    public BuyerService(BuyerProfileRepository profiles, UserRepository users, RegionCatalog regions) {
        this.profiles = profiles;
        this.users = users;
        this.regions = regions;
    }

    @Transactional(readOnly = true)
    public BuyerProfileResponse get(UUID userId) {
        return toResponse(requireBuyer(userId), profile(userId));
    }

    @Transactional
    public BuyerProfileResponse update(UUID userId, UpdateBuyerProfileRequest r) {
        User user = requireBuyer(userId);
        BuyerProfile p = profile(userId);
        if (r.buyerType() != null) p.setBuyerType(r.buyerType());
        if (r.businessName() != null) p.setBusinessName(r.businessName().trim());
        if (r.contactPerson() != null) p.setContactPerson(r.contactPerson().trim());
        if (r.tinNumber() != null) p.setTinNumber(r.tinNumber().trim());
        if (r.tradeLicenseNumber() != null) p.setTradeLicenseNumber(r.tradeLicenseNumber().trim());
        if (r.address() != null) p.setAddress(r.address().toEntity(regions));
        if (r.deliveryInstructions() != null) p.setDeliveryInstructions(r.deliveryInstructions());
        return toResponse(user, p);
    }

    private User requireBuyer(UUID userId) {
        return users.findById(userId).filter(u -> u.getRole() == Role.BUYER)
                .orElseThrow(() -> ApiException.forbidden("Only buyers can use this endpoint"));
    }

    private BuyerProfile profile(UUID userId) {
        return profiles.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Buyer profile"));
    }

    private BuyerProfileResponse toResponse(User user, BuyerProfile p) {
        return new BuyerProfileResponse(user.getId(), p.getBuyerType(), p.getBusinessName(), p.getContactPerson(),
                p.getTinNumber(), p.getTradeLicenseNumber(), AddressDto.from(p.getAddress(), regions),
                p.getDeliveryInstructions(), p.getCompletedOrders(), user.getVerificationStatus());
    }
}
