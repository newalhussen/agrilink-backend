package com.agrilink.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers: all amounts are ETB with two decimal places. */
public final class Money {

    public static final String CURRENCY = "ETB";
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
