package com.agrilink.marketplace;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Filters accepted by the listing search; every field is optional. */
public record ListingSearchCriteria(
        String q,
        UUID categoryId,
        UUID productId,
        UUID regionId,
        UUID farmerId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        QualityGrade grade,
        Boolean organic,
        LocalDate availableBy,
        BigDecimal minQuantity,
        ListingStatus status) {
}
