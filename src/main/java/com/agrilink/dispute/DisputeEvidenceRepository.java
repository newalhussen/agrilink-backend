package com.agrilink.dispute;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {

    List<DisputeEvidence> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);

    @Query("select count(e) > 0 from DisputeEvidence e where e.fileId = :fileId and ("
            + "e.dispute.order.buyer.id = :userId or e.dispute.order.farmer.id = :userId "
            + "or e.dispute.order.driver.id = :userId)")
    boolean isAccessibleTo(@Param("fileId") UUID fileId, @Param("userId") UUID userId);
}
