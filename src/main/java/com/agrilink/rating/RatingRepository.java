package com.agrilink.rating;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByOrderIdAndRaterIdAndTarget(UUID orderId, UUID raterId, RatingTarget target);

    long countByOrderIdAndRaterId(UUID orderId, UUID raterId);

    List<Rating> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    Page<Rating> findByRateeIdOrderByCreatedAtDesc(UUID rateeId, Pageable pageable);
}
