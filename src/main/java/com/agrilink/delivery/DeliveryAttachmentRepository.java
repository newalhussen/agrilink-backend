package com.agrilink.delivery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryAttachmentRepository extends JpaRepository<DeliveryAttachment, UUID> {

    List<DeliveryAttachment> findByDeliveryIdOrderByCreatedAtAsc(UUID deliveryId);

    @Query("select count(a) > 0 from DeliveryAttachment a where a.fileId = :fileId and ("
            + "a.delivery.order.buyer.id = :userId or a.delivery.order.farmer.id = :userId "
            + "or a.delivery.driver.id = :userId)")
    boolean isAccessibleTo(@Param("fileId") UUID fileId, @Param("userId") UUID userId);
}
