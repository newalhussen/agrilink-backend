package com.agrilink.order;

import com.agrilink.common.Address;
import com.agrilink.common.GeoUtils;
import com.agrilink.common.Money;
import com.agrilink.config.AgriLinkProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Delivery and platform fee rules, kept in one small class so they can be tuned (or replaced by a
 * zone/route table) without touching the order workflow.
 */
@Service
public class PricingService {

    public record DeliveryQuote(BigDecimal distanceKm, BigDecimal fee) {}

    private final AgriLinkProperties.Pricing pricing;

    public PricingService(AgriLinkProperties properties) {
        this.pricing = properties.pricing();
    }

    /**
     * fee = base + perKm * distance + perKg * weight, never below the minimum, rounded up to the next
     * rounding step (ETB 10 by default). Distance is road-adjusted great-circle distance, or a
     * configured default when either end has no GPS point.
     */
    public DeliveryQuote deliveryQuote(Address pickup, Address dropoff, BigDecimal weightKg) {
        BigDecimal straight = GeoUtils.distanceKm(pickup, dropoff);
        BigDecimal distance = straight == null
                ? pricing.defaultDistanceKm()
                : straight.multiply(pricing.roadDistanceFactor());
        distance = distance.setScale(2, RoundingMode.HALF_UP);
        BigDecimal raw = pricing.deliveryBaseFee()
                .add(pricing.deliveryPerKm().multiply(distance))
                .add(pricing.deliveryPerKg().multiply(weightKg));
        BigDecimal fee = raw.max(pricing.deliveryMinFee());
        BigDecimal step = BigDecimal.valueOf(Math.max(1, pricing.roundingStep()));
        fee = fee.divide(step, 0, RoundingMode.CEILING).multiply(step);
        return new DeliveryQuote(distance, Money.scale(fee));
    }

    /** Platform fee charged to the buyer on top of the goods value (2% by default). */
    public BigDecimal platformFee(BigDecimal subtotal) {
        return Money.scale(subtotal.multiply(pricing.platformFeePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }
}
