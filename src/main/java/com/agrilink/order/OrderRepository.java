package com.agrilink.order;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Override
    @EntityGraph(attributePaths = {"buyer", "farmer", "driver"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "farmer", "driver", "items"})
    Optional<Order> findWithDetailsById(UUID id);

    /** Serialises concurrent state changes on one order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") UUID id);

    @Query(value = "select nextval('order_number_seq')", nativeQuery = true)
    long nextOrderSequence();

    @Query("select o.id from Order o where o.status = com.agrilink.order.OrderStatus.PENDING "
            + "and o.farmerResponseDeadline < :now")
    List<UUID> findPendingPastDeadline(@Param("now") Instant now);

    @Query("select o.id from Order o where o.status in (com.agrilink.order.OrderStatus.ACCEPTED, "
            + "com.agrilink.order.OrderStatus.PAYMENT_PENDING) and o.paymentDeadline < :now")
    List<UUID> findUnpaidPastDeadline(@Param("now") Instant now);

    @Query("select o.id from Order o where o.status = com.agrilink.order.OrderStatus.DELIVERED "
            + "and o.checkWindowEndsAt < :now")
    List<UUID> findDeliveredPastCheckWindow(@Param("now") Instant now);

    long countByStatus(OrderStatus status);

    long countByStatusIn(Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.createdAt >= :since "
            + "and o.status not in (com.agrilink.order.OrderStatus.PENDING, com.agrilink.order.OrderStatus.ACCEPTED, "
            + "com.agrilink.order.OrderStatus.PAYMENT_PENDING, com.agrilink.order.OrderStatus.REJECTED, "
            + "com.agrilink.order.OrderStatus.CANCELLED, com.agrilink.order.OrderStatus.EXPIRED)")
    BigDecimal sumFundedOrderValueSince(@Param("since") Instant since);

    @Query("select coalesce(sum(o.subtotalAmount), 0) from Order o where o.farmer.id = :userId "
            + "and o.status in :statuses")
    BigDecimal sumSubtotalForFarmer(@Param("userId") UUID userId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.deliveryFee), 0) from Order o where o.driver.id = :userId "
            + "and o.status in :statuses")
    BigDecimal sumDeliveryFeeForDriver(@Param("userId") UUID userId,
                                       @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countGroupedByStatus();
}
