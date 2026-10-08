package com.agrilink.order;

import com.agrilink.user.Role;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    /** Orders visible to a non-admin user: the ones they take part in as buyer, farmer or driver. */
    public static Specification<Order> forParticipant(UUID userId, Role role) {
        return (root, query, cb) -> switch (role) {
            case BUYER -> cb.equal(root.get("buyer").get("id"), userId);
            case FARMER -> cb.equal(root.get("farmer").get("id"), userId);
            case DRIVER -> cb.equal(root.get("driver").get("id"), userId);
            case ADMIN -> cb.conjunction();
        };
    }

    public static Specification<Order> withFilters(Collection<OrderStatus> statuses, Instant from, Instant to,
                                                   UUID buyerId, UUID farmerId, UUID driverId, String q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to));
            }
            if (buyerId != null) {
                p.add(cb.equal(root.get("buyer").get("id"), buyerId));
            }
            if (farmerId != null) {
                p.add(cb.equal(root.get("farmer").get("id"), farmerId));
            }
            if (driverId != null) {
                p.add(cb.equal(root.get("driver").get("id"), driverId));
            }
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.get("orderNumber")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
