package com.agrilink.marketplace;

import com.agrilink.common.PageResponse;
import com.agrilink.marketplace.ListingDtos.AddPhotoRequest;
import com.agrilink.marketplace.ListingDtos.ChangeStatusRequest;
import com.agrilink.marketplace.ListingDtos.CreateListingRequest;
import com.agrilink.marketplace.ListingDtos.ListingResponse;
import com.agrilink.marketplace.ListingDtos.UpdateListingRequest;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class ListingController {

    private final ListingService listings;
    private final ListingMapper mapper;

    public ListingController(ListingService listings, ListingMapper mapper) {
        this.listings = listings;
        this.mapper = mapper;
    }

    /**
     * Public marketplace search. {@code lat}/{@code lon} (the buyer's position) add {@code distanceKm} to each
     * result. {@code sort}: newest (default), price_asc, price_desc, quantity_desc, available_soon.
     */
    @GetMapping("/listings")
    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID regionId,
            @RequestParam(required = false) UUID farmerId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) QualityGrade grade,
            @RequestParam(required = false) Boolean organic,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableBy,
            @RequestParam(required = false) BigDecimal minQuantity,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(required = false) String sort,
            Pageable pageable) {
        ListingSearchCriteria criteria = new ListingSearchCriteria(q, categoryId, productId, regionId, farmerId,
                minPrice, maxPrice, grade, organic, availableBy, minQuantity, null);
        var page = listings.searchMarketplace(criteria, sort, pageable);
        var responses = mapper.toResponses(page.getContent(), lat, lon);
        return new PageResponse<>(responses, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }

    @GetMapping("/listings/{id}")
    @Transactional(readOnly = true)
    public ListingResponse get(@PathVariable UUID id, @RequestParam(required = false) Double lat,
                               @RequestParam(required = false) Double lon) {
        Listing listing = listings.getVisible(id, AuthContext.optionalUserId().orElse(null), AuthContext.isAdmin());
        return mapper.toResponse(listing, lat, lon);
    }

    @PostMapping("/listings")
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@Valid @RequestBody CreateListingRequest request) {
        Listing listing = listings.create(AuthContext.userId(), request);
        return mapper.toResponse(listing, null, null);
    }

    @PatchMapping("/listings/{id}")
    @PreAuthorize("hasRole('FARMER')")
    public ListingResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateListingRequest request) {
        return mapper.toResponse(listings.update(AuthContext.userId(), id, request), null, null);
    }

    /** Publish (ACTIVE), pause (PAUSED), unpublish (DRAFT) or remove (REMOVED) a listing. */
    @PutMapping("/listings/{id}/status")
    @PreAuthorize("hasRole('FARMER')")
    public ListingResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return mapper.toResponse(listings.changeStatusAsFarmer(AuthContext.userId(), id, request.status()), null, null);
    }

    @DeleteMapping("/listings/{id}")
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        listings.changeStatusAsFarmer(AuthContext.userId(), id, ListingStatus.REMOVED);
    }

    @PostMapping("/listings/{id}/photos")
    @PreAuthorize("hasRole('FARMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse addPhoto(@PathVariable UUID id, @Valid @RequestBody AddPhotoRequest request) {
        Listing l = listings.addPhoto(AuthContext.userId(), id, request.fileId(), Boolean.TRUE.equals(request.primary()));
        return mapper.toResponse(l, null, null);
    }

    @DeleteMapping("/listings/{id}/photos/{photoId}")
    @PreAuthorize("hasRole('FARMER')")
    public ListingResponse removePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        return mapper.toResponse(listings.removePhoto(AuthContext.userId(), id, photoId), null, null);
    }

    /** The signed-in farmer's own listings in any status. */
    @GetMapping("/farmers/me/listings")
    @PreAuthorize("hasRole('FARMER')")
    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> mine(@RequestParam(required = false) ListingStatus status, Pageable pageable) {
        ListingSearchCriteria criteria = new ListingSearchCriteria(null, null, null, null, AuthContext.userId(), null,
                null, null, null, null, null, status);
        var page = listings.searchManagement(criteria, pageable);
        return new PageResponse<>(mapper.toResponses(page.getContent(), null, null), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
