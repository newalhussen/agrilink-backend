package com.agrilink.dispute;

import com.agrilink.dispute.DisputeEnums.DisputeStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, UUID>, JpaSpecificationExecutor<Dispute> {

    @Override
    @EntityGraph(attributePaths = {"order", "order.buyer", "order.farmer", "raisedBy", "againstUser"})
    Page<Dispute> findAll(Specification<Dispute> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.farmer", "order.driver", "raisedBy", "againstUser"})
    Optional<Dispute> findWithDetailsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> lockById(@Param("id") UUID id);

    @Query(value = "select nextval('dispute_number_seq')", nativeQuery = true)
    long nextNumber();

    @Query("select d from Dispute d where d.order.id = :orderId order by d.createdAt desc")
    List<Dispute> findByOrderId(@Param("orderId") UUID orderId);

    long countByStatusIn(Collection<DisputeStatus> statuses);

    @Query("select count(d) from Dispute d where d.status in :statuses and d.dueAt < :now")
    long countOverdue(@Param("statuses") Collection<DisputeStatus> statuses, @Param("now") Instant now);
}
