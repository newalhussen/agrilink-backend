package com.agrilink.delivery;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class DeliverySpecifications {

    private DeliverySpecifications() {
    }

    /** Job board: open jobs the driver's vehicle can carry, optionally near a region or above a minimum fee. */
    public static Specification<Delivery> open(BigDecimal maxWeightKg, UUID pickupRegionId, BigDecimal minFee) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), DeliveryStatus.OPEN));
            if (maxWeightKg != null) {
                p.add(cb.lessThanOrEqualTo(root.get("totalWeightKg"), maxWeightKg));
            }
            if (pickupRegionId != null) {
                p.add(cb.equal(root.get("pickupAddress").get("regionId"), pickupRegionId));
            }
            if (minFee != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("driverFee"), minFee));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    public static Specification<Delivery> forDriver(UUID driverId, Set<DeliveryStatus> statuses) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("driver").get("id"), driverId));
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    public static Specification<Delivery> admin(Set<DeliveryStatus> statuses, UUID driverId, String q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            if (driverId != null) {
                p.add(cb.equal(root.get("driver").get("id"), driverId));
            }
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.get("order").get("orderNumber")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
