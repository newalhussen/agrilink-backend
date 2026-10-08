package com.agrilink.delivery;

import jakarta.persistence.LockModeType;
import java.util.Collection;
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

public interface DeliveryRepository extends JpaRepository<Delivery, UUID>, JpaSpecificationExecutor<Delivery> {

    @Override
    @EntityGraph(attributePaths = {"order", "order.buyer", "order.farmer", "driver"})
    Page<Delivery> findAll(Specification<Delivery> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.farmer", "order.items", "driver"})
    Optional<Delivery> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.farmer", "order.items", "driver"})
    Optional<Delivery> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> lockById(@Param("id") UUID id);

    @Query("select d.order.id from Delivery d where d.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    long countByDriverIdAndStatusIn(UUID driverId, Collection<DeliveryStatus> statuses);

    long countByStatus(DeliveryStatus status);
}
