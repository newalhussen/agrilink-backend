package com.agrilink.buyer;

import com.agrilink.common.AddressDto;
import com.agrilink.user.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class BuyerDtos {

    private BuyerDtos() {
    }

    public record UpdateBuyerProfileRequest(
            BuyerType buyerType,
            @Size(max = 150) String businessName,
            @Size(max = 150) String contactPerson,
            @Size(max = 30) String tinNumber,
            @Size(max = 60) String tradeLicenseNumber,
            @Valid AddressDto address,
            @Size(max = 500) String deliveryInstructions) {}

    public record BuyerProfileResponse(
            UUID userId,
            BuyerType buyerType,
            String businessName,
            String contactPerson,
            String tinNumber,
            String tradeLicenseNumber,
            AddressDto address,
            String deliveryInstructions,
            int completedOrders,
            VerificationStatus verificationStatus) {}
}
