package com.agrilink.farmer;

import com.agrilink.common.AddressDto;
import com.agrilink.marketplace.CatalogDtos.ProductResponse;
import com.agrilink.user.VerificationStatus;
import com.agrilink.wallet.PayoutMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FarmerDtos {

    private FarmerDtos() {
    }

    /** Partial update: fields left null are not changed. */
    public record UpdateFarmerProfileRequest(
            FarmerType farmerType,
            @Size(max = 150) String farmName,
            @Min(1) Integer memberCount,
            @Valid AddressDto address,
            @DecimalMin("0.0") BigDecimal landSizeHectares,
            Boolean irrigated,
            @DecimalMin("0.0") BigDecimal expectedMonthlySupplyKg,
            @Size(max = 1000) String bio,
            @Size(max = 30) String faydaIdNumber,
            PayoutMethod payoutMethod,
            @Size(max = 150) String payoutAccountName,
            @Size(max = 40) String payoutAccountNumber,
            Set<UUID> productIds) {}

    public record FarmerProfileResponse(
            UUID userId,
            FarmerType farmerType,
            String farmName,
            Integer memberCount,
            AddressDto address,
            BigDecimal landSizeHectares,
            boolean irrigated,
            BigDecimal expectedMonthlySupplyKg,
            String bio,
            String faydaIdNumber,
            PayoutMethod payoutMethod,
            String payoutAccountName,
            String payoutAccountNumber,
            int completedTrades,
            VerificationStatus verificationStatus,
            List<ProductResponse> products) {}

    /** What a buyer may see about a farmer: no phone, ID or payout data. */
    public record PublicFarmerResponse(
            UUID userId,
            String fullName,
            String farmName,
            FarmerType farmerType,
            Integer memberCount,
            AddressDto address,
            BigDecimal ratingAverage,
            int ratingCount,
            int completedTrades,
            boolean verified,
            String profilePhotoUrl,
            List<ProductResponse> products) {}
}
