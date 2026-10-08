package com.agrilink.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle:
 * PENDING -> ACCEPTED -> PAYMENT_PENDING -> PAID -> READY_FOR_PICKUP -> PICKED_UP -> IN_TRANSIT -> DELIVERED -> COMPLETED
 * with REJECTED / CANCELLED / EXPIRED exits and DISPUTED as a side state that is resolved into
 * COMPLETED or CANCELLED.
 */
public enum OrderStatus {
    PENDING,
    ACCEPTED,
    PAYMENT_PENDING,
    PAID,
    READY_FOR_PICKUP,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    DISPUTED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED || this == EXPIRED;
    }

    /** Money is in escrow (or about to be) from PAID until completion or a refund. */
    public boolean isFunded() {
        return this == PAID || this == READY_FOR_PICKUP || this == PICKED_UP || this == IN_TRANSIT
                || this == DELIVERED || this == DISPUTED;
    }

    /** Statuses from which a participant may raise a dispute. */
    public static final Set<OrderStatus> DISPUTABLE = EnumSet.of(PAID, READY_FOR_PICKUP, PICKED_UP, IN_TRANSIT,
            DELIVERED);

    /** Statuses in which goods are still on the farm, so reserved stock can still be returned. */
    public boolean isBeforePickup() {
        return this == PENDING || this == ACCEPTED || this == PAYMENT_PENDING || this == PAID
                || this == READY_FOR_PICKUP;
    }
}
