package com.agrilink.marketplace;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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

public interface ListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {

    @Override
    @EntityGraph(attributePaths = {"farmer", "product", "product.category"})
    Page<Listing> findAll(Specification<Listing> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"farmer", "product", "product.category"})
    Optional<Listing> findWithDetailsById(UUID id);

    /** Locks rows in id order so concurrent orders for the same listings cannot deadlock or oversell. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id in :ids order by l.id")
    List<Listing> lockAllByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("select l.id from Listing l where l.status in :statuses and l.availableUntil < :today")
    List<UUID> findIdsPastAvailability(@Param("statuses") Collection<ListingStatus> statuses,
                                       @Param("today") LocalDate today);

    long countByStatus(ListingStatus status);
}
