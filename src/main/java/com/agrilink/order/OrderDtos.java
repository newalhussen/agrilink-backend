package com.agrilink.order;

import com.agrilink.common.AddressDto;
import com.agrilink.marketplace.Unit;
import com.agrilink.payment.PaymentMethod;
import com.agrilink.payment.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderItemRequest(
            @NotNull UUID listingId,
            @NotNull @DecimalMin(value = "0.001", message = "Quantity must be positive") BigDecimal quantity) {}

    /** Used for both price quotes and real orders. All items must belong to the same farmer. */
    public record CreateOrderRequest(
            @NotEmpty @Size(max = 20) List<@Valid OrderItemRequest> items,
            @NotNull @Valid AddressDto deliveryAddress,
            @Size(max = 150) String deliveryContactName,
            @Size(max = 20) String deliveryContactPhone,
            LocalDate requestedDeliveryDate,
            @Size(max = 1000) String notes) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record QuoteLine(UUID listingId, String title, Unit unit, BigDecimal quantity, BigDecimal unitPrice,
                            BigDecimal lineTotal, BigDecimal weightKg) {}

    /** Price breakdown shown before the buyer commits (goods + delivery + AgriLink fee). */
    public record QuoteResponse(List<QuoteLine> lines, BigDecimal subtotal, BigDecimal deliveryFee,
                                BigDecimal platformFee, BigDecimal total, String currency, BigDecimal totalWeightKg,
                                BigDecimal distanceKm, UUID farmerId) {}

    public record OrderItemResponse(UUID id, UUID listingId, UUID productId, String productName, String qualityGrade,
                                    Unit unit, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                                    BigDecimal weightKg) {}

    public record PartyView(UUID id, String fullName, String displayName, String phone, BigDecimal ratingAverage,
                            int ratingCount, boolean verified) {}

    public record Amounts(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal platformFee, BigDecimal total,
                          String currency) {}

    public record PaymentSummary(UUID id, PaymentStatus status, PaymentMethod method, BigDecimal amount,
                                 String failureReason, Instant expiresAt) {}

    /**
     * Handover codes are role-scoped: the farmer sees the pickup code, the buyer sees the delivery code,
     * and the driver sees neither (drivers must obtain them from the people at each end).
     */
    public record DeliverySummary(UUID id, String status, BigDecimal driverFee, BigDecimal distanceKm,
                                  LocalDate scheduledPickupDate, String pickupCode, String pickupQrToken,
                                  String deliveryCode, String deliveryQrToken, BigDecimal pickedUpWeightKg,
                                  Integer pickedUpCrateCount, Instant assignedAt, Instant pickedUpAt,
                                  Instant deliveredAt) {}

    public record Timestamps(Instant createdAt, Instant acceptedAt, Instant paidAt, Instant readyAt,
                             Instant pickedUpAt, Instant deliveredAt, Instant completedAt, Instant cancelledAt) {}

    public record Deadlines(Instant farmerResponseDeadline, Instant paymentDeadline, Instant checkWindowEndsAt) {}

    public record OrderResponse(
            UUID id,
            String orderNumber,
            OrderStatus status,
            String statusReason,
            PartyView buyer,
            PartyView farmer,
            PartyView driver,
            List<OrderItemResponse> items,
            Amounts amounts,
            BigDecimal totalWeightKg,
            AddressDto deliveryAddress,
            String deliveryContactName,
            String deliveryContactPhone,
            AddressDto pickupAddress,
            LocalDate requestedDeliveryDate,
            String buyerNotes,
            Deadlines deadlines,
            Timestamps timestamps,
            List<String> allowedActions,
            PaymentSummary payment,
            DeliverySummary delivery) {}

    public record OrderListItem(
            UUID id,
            String orderNumber,
            OrderStatus status,
            String itemsSummary,
            int itemCount,
            String buyerName,
            String farmerName,
            String driverName,
            BigDecimal totalAmount,
            String currency,
            BigDecimal totalWeightKg,
            String deliveryTown,
            Deadlines deadlines,
            Instant createdAt,
            List<String> allowedActions) {}

    public record TimelineEntry(OrderStatus from, OrderStatus to, Instant at, String actorRole, String note) {}
}
