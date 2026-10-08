package com.agrilink.region;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, UUID> {

    List<Region> findByActiveTrueOrderByNameEnAsc();

    java.util.Optional<Region> findByCode(String code);
}
