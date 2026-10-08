package com.agrilink.wallet;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Page<Payout> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Payout> findByStatusOrderByCreatedAtDesc(PayoutStatus status, Pageable pageable);

    Page<Payout> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(PayoutStatus status);
}
