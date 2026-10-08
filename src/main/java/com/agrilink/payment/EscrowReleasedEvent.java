package com.agrilink.payment;

import java.math.BigDecimal;
import java.util.UUID;

/** Published when escrow is paid out to the farmer's and driver's wallets. */
public record EscrowReleasedEvent(
        UUID orderId,
        String orderNumber,
        UUID farmerId,
        BigDecimal farmerAmount,
        UUID driverId,
        BigDecimal driverAmount) {
}
