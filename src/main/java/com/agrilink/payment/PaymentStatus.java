package com.agrilink.payment;

public enum PaymentStatus {
    /** Charge requested; waiting for the customer or provider to confirm. */
    PENDING,
    /** Money received and held in escrow until delivery is confirmed. */
    HELD,
    FAILED,
    CANCELLED,
    EXPIRED,
    /** Escrow paid out to farmer and driver (platform fee retained). */
    RELEASED,
    REFUNDED,
    /** Dispute settlement returned part of the money to the buyer. */
    PARTIALLY_REFUNDED;

    public boolean isActive() {
        return this == PENDING || this == HELD;
    }
}
