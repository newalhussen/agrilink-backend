package com.agrilink.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryResponse(UUID id, String slug, String nameEn, String nameAm, String nameOm, String icon,
                                   int sortOrder, boolean active) {
        public static CategoryResponse from(ProductCategory c) {
            return new CategoryResponse(c.getId(), c.getSlug(), c.getNameEn(), c.getNameAm(), c.getNameOm(),
                    c.getIcon(), c.getSortOrder(), c.isActive());
        }
    }

    public record ProductResponse(UUID id, String slug, String nameEn, String nameAm, String nameOm,
                                  UUID categoryId, String categoryName, Unit defaultUnit, String description,
                                  boolean active) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSlug(), p.getNameEn(), p.getNameAm(), p.getNameOm(),
                    p.getCategory().getId(), p.getCategory().getNameEn(), p.getDefaultUnit(), p.getDescription(),
                    p.isActive());
        }
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = "[a-z0-9-]+", message = "Use lowercase letters, digits and dashes") String slug,
            @NotBlank @Size(max = 100) String nameEn,
            @Size(max = 100) String nameAm,
            @Size(max = 100) String nameOm,
            @Size(max = 60) String icon,
            Integer sortOrder,
            Boolean active) {}

    public record ProductRequest(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9-]+", message = "Use lowercase letters, digits and dashes") String slug,
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 100) String nameEn,
            @Size(max = 100) String nameAm,
            @Size(max = 100) String nameOm,
            @NotNull Unit defaultUnit,
            @Size(max = 1000) String description,
            Boolean active) {}
}
