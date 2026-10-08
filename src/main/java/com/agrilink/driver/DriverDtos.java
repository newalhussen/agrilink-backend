package com.agrilink.driver;

import com.agrilink.user.VerificationStatus;
import com.agrilink.wallet.PayoutMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class DriverDtos {

    private DriverDtos() {
    }

    public record UpdateDriverProfileRequest(
            @Size(max = 60) String licenseNumber,
            LocalDate licenseExpiryDate,
            VehicleType vehicleType,
            @Size(max = 30) String vehiclePlate,
            @Size(max = 100) String vehicleMakeModel,
            @Min(1950) @Max(2100) Integer vehicleYear,
            @DecimalMin(value = "1.0", message = "Capacity must be at least 1 kg") BigDecimal capacityKg,
            Boolean refrigerated,
            UUID regionId,
            PayoutMethod payoutMethod,
            @Size(max = 150) String payoutAccountName,
            @Size(max = 40) String payoutAccountNumber) {}

    public record DriverProfileResponse(
            UUID userId,
            String licenseNumber,
            LocalDate licenseExpiryDate,
            VehicleType vehicleType,
            String vehiclePlate,
            String vehicleMakeModel,
            Integer vehicleYear,
            BigDecimal capacityKg,
            boolean refrigerated,
            UUID regionId,
            String regionName,
            DriverAvailability availability,
            Double currentLatitude,
            Double currentLongitude,
            Instant locationUpdatedAt,
            PayoutMethod payoutMethod,
            String payoutAccountName,
            String payoutAccountNumber,
            int completedDeliveries,
            VerificationStatus verificationStatus) {}

    public record AvailabilityRequest(@NotNull DriverAvailability availability) {}

    public record LocationRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {}
}
