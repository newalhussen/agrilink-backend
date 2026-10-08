package com.agrilink.marketplace;

import com.agrilink.common.AddressDto;
import com.agrilink.marketplace.CatalogDtos.ProductResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ListingDtos {

    private ListingDtos() {
    }

    public record CreateListingRequest(
            @NotNull UUID productId,
            @Size(max = 150) String title,
            @Size(max = 2000) String description,
            QualityGrade qualityGrade,
            @Size(max = 1000) String qualityNotes,
            @Size(max = 100) String packaging,
            LocalDate harvestDate,
            LocalDate availableFrom,
            LocalDate availableUntil,
            @NotNull Unit unit,
            @DecimalMin(value = "0.001", message = "Unit weight must be positive") BigDecimal unitWeightKg,
            @NotNull @DecimalMin(value = "0.001", message = "Quantity must be positive") BigDecimal quantity,
            @DecimalMin(value = "0.001") BigDecimal minOrderQuantity,
            @NotNull @DecimalMin(value = "0.01", message = "Price must be positive") BigDecimal pricePerUnit,
            Boolean organic,
            @Valid AddressDto address,
            List<UUID> photoFileIds,
            Boolean publish) {}

    /** Partial update: fields left null are not changed. {@code quantityAvailable} restocks or reduces stock. */
    public record UpdateListingRequest(
            @Size(max = 150) String title,
            @Size(max = 2000) String description,
            QualityGrade qualityGrade,
            @Size(max = 1000) String qualityNotes,
            @Size(max = 100) String packaging,
            LocalDate harvestDate,
            LocalDate availableFrom,
            LocalDate availableUntil,
            @DecimalMin(value = "0.001") BigDecimal unitWeightKg,
            @DecimalMin(value = "0.0") BigDecimal quantityAvailable,
            @DecimalMin(value = "0.001") BigDecimal minOrderQuantity,
            @DecimalMin(value = "0.01") BigDecimal pricePerUnit,
            Boolean organic,
            @Valid AddressDto address) {}

    public record ChangeStatusRequest(@NotNull ListingStatus status) {}

    public record AddPhotoRequest(@NotNull UUID fileId, Boolean primary) {}

    public record PhotoResponse(UUID id, UUID fileId, String url, boolean primary, int sortOrder) {}

    public record FarmerSummary(UUID id, String fullName, String farmName, BigDecimal ratingAverage, int ratingCount,
                                int completedTrades, boolean verified) {}

    public record ListingResponse(
            UUID id,
            ListingStatus status,
            ProductResponse product,
            String title,
            String description,
            QualityGrade qualityGrade,
            String qualityNotes,
            String packaging,
            LocalDate harvestDate,
            LocalDate availableFrom,
            LocalDate availableUntil,
            Unit unit,
            BigDecimal unitWeightKg,
            BigDecimal quantityTotal,
            BigDecimal quantityAvailable,
            BigDecimal minOrderQuantity,
            BigDecimal pricePerUnit,
            String currency,
            boolean organic,
            AddressDto address,
            List<PhotoResponse> photos,
            FarmerSummary farmer,
            Double distanceKm,
            Instant createdAt) {}
}
