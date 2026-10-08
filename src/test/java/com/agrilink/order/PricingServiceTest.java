package com.agrilink.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrilink.TestSupport;
import com.agrilink.common.Address;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PricingService pricing = new PricingService(TestSupport.properties());

    @Test
    void platformFeeIsTwoPercentOfGoods() {
        // Matches the design: ETB 18,400 of tomatoes carries an ETB 368 AgriLink fee.
        assertThat(pricing.platformFee(new BigDecimal("18400.00"))).isEqualByComparingTo("368.00");
        assertThat(pricing.platformFee(new BigDecimal("333.33"))).isEqualByComparingTo("6.67");
    }

    @Test
    void deliveryFeeUsesDefaultDistanceWithoutCoordinates() {
        Address none = new Address();
        // 600 base + 15 * 100 km + 0.20 * 400 kg = 2180
        var quote = pricing.deliveryQuote(none, none, new BigDecimal("400"));
        assertThat(quote.distanceKm()).isEqualByComparingTo("100.00");
        assertThat(quote.fee()).isEqualByComparingTo("2180.00");
    }

    @Test
    void deliveryFeeUsesRoadAdjustedDistanceBetweenGpsPoints() {
        Address meki = new Address(null, null, null, "Meki", null, 8.15, 38.82);
        Address bole = new Address(null, null, null, "Addis Ababa", null, 8.99, 38.79);
        var quote = pricing.deliveryQuote(meki, bole, new BigDecimal("402"));
        // About 93 km straight line * 1.3 road factor.
        assertThat(quote.distanceKm()).isBetween(new BigDecimal("115"), new BigDecimal("130"));
        assertThat(quote.fee().remainder(BigDecimal.TEN)).isEqualByComparingTo("0");
        assertThat(quote.fee()).isGreaterThan(new BigDecimal("2000"));
    }

    @Test
    void feeNeverDropsBelowMinimum() {
        Address a = new Address(null, null, null, "A", null, 9.0, 38.0);
        Address b = new Address(null, null, null, "B", null, 9.0, 38.0);
        // Zero distance, tiny load: base fee alone is 600, above the 500 minimum, and rounded to a step of 10.
        assertThat(pricing.deliveryQuote(a, b, new BigDecimal("1")).fee()).isEqualByComparingTo("610.00");
    }
}
