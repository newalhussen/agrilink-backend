package com.agrilink.dispute;

import com.agrilink.dispute.DisputeEnums.DisputeStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class DisputeSpecifications {

    private DisputeSpecifications() {
    }

    public static Specification<Dispute> forParticipant(UUID userId, Set<DisputeStatus> statuses) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.or(cb.equal(root.get("order").get("buyer").get("id"), userId),
                    cb.equal(root.get("order").get("farmer").get("id"), userId),
                    cb.equal(root.get("order").get("driver").get("id"), userId)));
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** {@code overdueAt}: when set, only open disputes whose resolution target has already passed. */
    public static Specification<Dispute> admin(Set<DisputeStatus> statuses, Instant overdueAt, String q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (statuses != null && !statuses.isEmpty()) {
                p.add(root.get("status").in(statuses));
            }
            if (overdueAt != null) {
                p.add(root.get("status").in(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW));
                p.add(cb.lessThan(root.get("dueAt"), overdueAt));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("disputeNumber")), like),
                        cb.like(cb.lower(root.get("order").get("orderNumber")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
