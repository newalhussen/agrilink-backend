package com.agrilink.marketplace;

import com.agrilink.user.AccountStatus;
import com.agrilink.user.User;
import com.agrilink.user.VerificationStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class ListingSpecifications {

    private ListingSpecifications() {
    }

    /**
     * Public marketplace view: active listings with stock, within their availability window, from active
     * farmers (and verified ones when configured).
     */
    public static Specification<Listing> marketplace(ListingSearchCriteria c, boolean requireVerifiedFarmers,
                                                     LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Join<Listing, User> farmer = root.join("farmer");
            p.add(cb.equal(root.get("status"), ListingStatus.ACTIVE));
            p.add(cb.greaterThan(root.get("quantityAvailable"), 0));
            p.add(cb.or(cb.isNull(root.get("availableUntil")), cb.greaterThanOrEqualTo(root.get("availableUntil"), today)));
            p.add(cb.equal(farmer.get("accountStatus"), AccountStatus.ACTIVE));
            if (requireVerifiedFarmers) {
                p.add(cb.equal(farmer.get("verificationStatus"), VerificationStatus.VERIFIED));
            }
            applyFilters(c, p, root, farmer, cb);
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /** Management view (farmer's own listings, admin listing screen): any status, caller-selected filters. */
    public static Specification<Listing> management(ListingSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Join<Listing, User> farmer = root.join("farmer");
            if (c.status() != null) {
                p.add(cb.equal(root.get("status"), c.status()));
            }
            applyFilters(c, p, root, farmer, cb);
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static void applyFilters(ListingSearchCriteria c, List<Predicate> p,
                                     jakarta.persistence.criteria.Root<Listing> root, Join<Listing, User> farmer,
                                     jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (c.farmerId() != null) {
            p.add(cb.equal(farmer.get("id"), c.farmerId()));
        }
        if (c.productId() != null || c.categoryId() != null || (c.q() != null && !c.q().isBlank())) {
            Join<Listing, Product> product = root.join("product");
            if (c.productId() != null) {
                p.add(cb.equal(product.get("id"), c.productId()));
            }
            if (c.categoryId() != null) {
                p.add(cb.equal(product.get("category").get("id"), c.categoryId()));
            }
            if (c.q() != null && !c.q().isBlank()) {
                String like = "%" + c.q().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(product.get("nameEn")), like),
                        cb.like(cb.lower(cb.coalesce(product.get("nameAm"), "")), like),
                        cb.like(cb.lower(cb.coalesce(product.get("nameOm"), "")), like),
                        cb.like(cb.lower(farmer.get("fullName")), like)));
            }
        }
        if (c.regionId() != null) {
            p.add(cb.equal(root.get("address").get("regionId"), c.regionId()));
        }
        if (c.minPrice() != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("pricePerUnit"), c.minPrice()));
        }
        if (c.maxPrice() != null) {
            p.add(cb.lessThanOrEqualTo(root.get("pricePerUnit"), c.maxPrice()));
        }
        if (c.grade() != null) {
            p.add(cb.equal(root.get("qualityGrade"), c.grade()));
        }
        if (c.organic() != null) {
            p.add(cb.equal(root.get("organic"), c.organic()));
        }
        if (c.availableBy() != null) {
            p.add(cb.lessThanOrEqualTo(root.get("availableFrom"), c.availableBy()));
        }
        if (c.minQuantity() != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("quantityAvailable"), c.minQuantity()));
        }
    }
}
