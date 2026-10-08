package com.agrilink.verification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationReviewRepository extends JpaRepository<VerificationReview, UUID> {

    List<VerificationReview> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
