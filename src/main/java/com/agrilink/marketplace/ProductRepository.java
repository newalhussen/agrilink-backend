package com.agrilink.marketplace;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySlug(String slug);

    java.util.Optional<Product> findBySlug(String slug);

    @Query("""
            select p from Product p join fetch p.category c
            where p.active = true
              and (:categoryId is null or c.id = :categoryId)
              and (:q is null or lower(p.nameEn) like :q or lower(coalesce(p.nameAm, '')) like :q
                   or lower(coalesce(p.nameOm, '')) like :q)
            """)
    List<Product> search(@Param("categoryId") UUID categoryId, @Param("q") String q, Sort sort);

    @Query("select p from Product p join fetch p.category where p.id in :ids")
    List<Product> findAllWithCategory(@Param("ids") Iterable<UUID> ids);
}
