package com.agrilink.delivery;

import java.math.BigDecimal;
import java.util.UUID;

/** Published inside the transaction when a delivery moves; drives driver/buyer/farmer notifications. */
public record DeliveryStatusChangedEvent(
        UUID deliveryId,
        UUID orderId,
        String orderNumber,
        UUID buyerId,
        UUID farmerId,
        UUID driverId,
        String driverName,
        DeliveryStatus status,
        BigDecimal weightKg) {
}
