package com.agrilink.payment;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<Payment> findFirstByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

    Optional<Payment> findByTransactionReference(String transactionReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.transactionReference = :ref")
    Optional<Payment> lockByTransactionReference(@Param("ref") String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") UUID id);

    Page<Payment> findByPayerIdOrderByCreatedAtDesc(UUID payerId, Pageable pageable);

    Page<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = com.agrilink.payment.PaymentStatus.HELD")
    BigDecimal sumHeld();

    @Query("select coalesce(sum(p.releasedFarmerAmount + p.releasedDriverAmount), 0) from Payment p "
            + "where p.releasedAt >= :since")
    BigDecimal sumReleasedSince(@Param("since") Instant since);

    @Query("select coalesce(sum(p.platformFeeRetained), 0) from Payment p where p.releasedAt >= :since")
    BigDecimal sumPlatformFeeSince(@Param("since") Instant since);

    long countByStatus(PaymentStatus status);
}
