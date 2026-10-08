package com.agrilink.delivery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, UUID> {

    List<DeliveryEvent> findByDeliveryIdOrderByCreatedAtAsc(UUID deliveryId);
}
