package com.agrilink.farmer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, UUID> {

    Optional<FarmerProfile> findByUserId(UUID userId);

    java.util.List<FarmerProfile> findByUserIdIn(java.util.Collection<UUID> userIds);

    @Query("select f from FarmerProfile f left join fetch f.products where f.userId = :userId")
    Optional<FarmerProfile> findByUserIdWithProducts(@Param("userId") UUID userId);
}
