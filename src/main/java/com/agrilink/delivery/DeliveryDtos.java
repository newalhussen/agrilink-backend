package com.agrilink.delivery;

import com.agrilink.common.AddressDto;
import com.agrilink.marketplace.Unit;
import com.agrilink.order.OrderDtos.PartyView;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    public record PickupRequest(
            @NotBlank @Size(max = 64) String code,
            @NotNull @DecimalMin(value = "0.001", message = "Weighed quantity must be positive") BigDecimal weighedKg,
            @Min(0) Integer crateCount,
            @Size(max = 500) String note,
            List<UUID> evidenceFileIds) {}

    public record DeliverRequest(
            @NotBlank @Size(max = 64) String code,
            @Size(max = 500) String note,
            List<UUID> evidenceFileIds) {}

    public record LocationPingRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Size(max = 500) String note) {}

    public record AssignDriverRequest(@NotNull UUID driverId) {}

    public record LoadLine(String name, BigDecimal quantity, Unit unit, BigDecimal weightKg) {}

    public record Stop(AddressDto address, String contactName, String contactPhone) {}

    /** Entry on the driver job board: coarse locations only, no contacts or codes. */
    public record JobResponse(
            UUID id,
            String orderNumber,
            DeliveryStatus status,
            AddressDto pickup,
            AddressDto dropoff,
            BigDecimal distanceKm,
            BigDecimal totalWeightKg,
            BigDecimal driverFee,
            LocalDate scheduledPickupDate,
            List<LoadLine> load,
            Instant createdAt) {}

    /**
     * Full delivery view. Codes are role-scoped: farmer sees the pickup code, buyer the delivery code,
     * the driver neither, admin both.
     */
    public record DeliveryResponse(
            UUID id,
            UUID orderId,
            String orderNumber,
            String orderStatus,
            DeliveryStatus status,
            Stop pickup,
            Stop dropoff,
            BigDecimal distanceKm,
            BigDecimal totalWeightKg,
            BigDecimal driverFee,
            LocalDate scheduledPickupDate,
            List<LoadLine> load,
            PartyView driver,
            String pickupCode,
            String pickupQrToken,
            String deliveryCode,
            String deliveryQrToken,
            BigDecimal pickedUpWeightKg,
            Integer pickedUpCrateCount,
            String pickupNote,
            String deliveryNote,
            Instant assignedAt,
            Instant pickedUpAt,
            Instant deliveredAt,
            int pickupFailedAttempts,
            int deliveryFailedAttempts,
            List<String> allowedActions) {}

    public record DeliveryEventResponse(String type, Double latitude, Double longitude, String note, Instant at) {}
}
