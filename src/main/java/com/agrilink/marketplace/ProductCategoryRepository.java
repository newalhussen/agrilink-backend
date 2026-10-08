package com.agrilink.marketplace;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    List<ProductCategory> findByActiveTrueOrderBySortOrderAscNameEnAsc();

    boolean existsBySlug(String slug);
}
