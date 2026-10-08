package com.agrilink.order;

import com.agrilink.user.Role;
import java.util.UUID;

/**
 * Published (synchronously, inside the transaction) whenever an order changes status. Delivery,
 * notification and statistics code reacts to this instead of being called by the order service.
 */
public record OrderStatusChangedEvent(
        UUID orderId,
        String orderNumber,
        UUID buyerId,
        UUID farmerId,
        UUID driverId,
        OrderStatus from,
        OrderStatus to,
        UUID actorId,
        Role actorRole,
        String reason) {
}
