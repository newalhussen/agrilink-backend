package com.agrilink.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** Published inside the transaction whenever a payment changes state. */
public record PaymentStatusChangedEvent(
        UUID paymentId,
        UUID orderId,
        String orderNumber,
        UUID buyerId,
        UUID farmerId,
        PaymentStatus from,
        PaymentStatus to,
        BigDecimal amount,
        String reason) {
}
