package com.agrilink.delivery;

public enum DeliveryStatus {
    /** Posted to the driver job board, waiting for a driver. */
    OPEN,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

    public boolean isActive() {
        return this == ASSIGNED || this == PICKED_UP || this == IN_TRANSIT;
    }
}
