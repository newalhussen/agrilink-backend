package com.agrilink.marketplace;

import java.math.BigDecimal;

/** Selling unit. Units without a fixed weight (crate, sack, tray...) need a farmer-supplied kg per unit. */
public enum Unit {
    KG("1"),
    QUINTAL("100"),
    TON("1000"),
    LITER("1"),
    CRATE(null),
    SACK(null),
    TRAY(null),
    BUNCH(null),
    PIECE(null);

    private final BigDecimal defaultWeightKg;

    Unit(String defaultWeightKg) {
        this.defaultWeightKg = defaultWeightKg == null ? null : new BigDecimal(defaultWeightKg);
    }

    /** Weight in kg of one unit, or {@code null} when it varies by product and packaging. */
    public BigDecimal defaultWeightKg() {
        return defaultWeightKg;
    }
}
