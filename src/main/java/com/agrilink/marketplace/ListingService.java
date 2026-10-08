package com.agrilink.marketplace;

import com.agrilink.common.Address;
import com.agrilink.common.ApiException;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.farmer.FarmerProfile;
import com.agrilink.farmer.FarmerProfileRepository;
import com.agrilink.file.FilePurpose;
import com.agrilink.file.FileService;
import com.agrilink.marketplace.ListingDtos.CreateListingRequest;
import com.agrilink.marketplace.ListingDtos.UpdateListingRequest;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingService {

    /** Ethiopia has a single time zone; "today" for availability windows is local time. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final ListingRepository listings;
    private final UserRepository users;
    private final FarmerProfileRepository farmerProfiles;
    private final CatalogService catalog;
    private final FileService files;
    private final RegionCatalog regions;
    private final AgriLinkProperties.Marketplace marketplaceProps;
    private final Clock clock;

    public ListingService(ListingRepository listings, UserRepository users, FarmerProfileRepository farmerProfiles,
                          CatalogService catalog, FileService files, RegionCatalog regions,
                          AgriLinkProperties properties, Clock clock) {
        this.listings = listings;
        this.users = users;
        this.farmerProfiles = farmerProfiles;
        this.catalog = catalog;
        this.files = files;
        this.regions = regions;
        this.marketplaceProps = properties.marketplace();
        this.clock = clock;
    }

    @Transactional
    public Listing create(UUID farmerId, CreateListingRequest r) {
        User farmer = requireFarmer(farmerId);
        Product product = catalog.requireActiveProduct(r.productId());
        Listing listing = new Listing(farmer, product);
        listing.setTitle(r.title() == null || r.title().isBlank() ? product.getNameEn() : r.title().trim());
        listing.setDescription(r.description());
        listing.setQualityGrade(r.qualityGrade() == null ? QualityGrade.A : r.qualityGrade());
        listing.setQualityNotes(r.qualityNotes());
        listing.setPackaging(r.packaging());
        listing.setHarvestDate(r.harvestDate());
        LocalDate today = today();
        LocalDate from = r.availableFrom() == null ? today : r.availableFrom();
        validateWindow(from, r.availableUntil());
        listing.setAvailableFrom(from);
        listing.setAvailableUntil(r.availableUntil());
        listing.setUnit(r.unit());
        listing.setUnitWeightKg(resolveUnitWeight(r.unit(), r.unitWeightKg()));
        listing.setQuantityTotal(r.quantity());
        listing.setQuantityAvailable(r.quantity());
        BigDecimal min = r.minOrderQuantity() == null ? BigDecimal.ONE.min(r.quantity()) : r.minOrderQuantity();
        if (min.compareTo(r.quantity()) > 0) {
            throw ApiException.badRequest("Minimum order cannot be larger than the quantity offered");
        }
        listing.setMinOrderQuantity(min);
        listing.setPricePerUnit(r.pricePerUnit());
        listing.setOrganic(Boolean.TRUE.equals(r.organic()));
        listing.setAddress(r.address() != null ? r.address().toEntity(regions) : defaultAddress(farmerId));
        boolean publish = r.publish() == null || r.publish();
        if (publish) {
            listing.publish(Instant.now(clock));
        } else {
            listing.setStatus(ListingStatus.DRAFT);
        }
        if (r.photoFileIds() != null) {
            int order = 0;
            for (UUID fileId : r.photoFileIds()) {
                files.requireOwned(fileId, farmerId, FilePurpose.LISTING_PHOTO);
                listing.getPhotos().add(new ListingPhoto(listing, fileId, order, order == 0));
                order++;
            }
        }
        return listings.save(listing);
    }

    @Transactional
    public Listing update(UUID farmerId, UUID listingId, UpdateListingRequest r) {
        Listing l = requireOwn(farmerId, listingId);
        assertEditable(l);
        if (r.title() != null && !r.title().isBlank()) l.setTitle(r.title().trim());
        if (r.description() != null) l.setDescription(r.description());
        if (r.qualityGrade() != null) l.setQualityGrade(r.qualityGrade());
        if (r.qualityNotes() != null) l.setQualityNotes(r.qualityNotes());
        if (r.packaging() != null) l.setPackaging(r.packaging());
        if (r.harvestDate() != null) l.setHarvestDate(r.harvestDate());
        LocalDate from = r.availableFrom() != null ? r.availableFrom() : l.getAvailableFrom();
        LocalDate until = r.availableUntil() != null ? r.availableUntil() : l.getAvailableUntil();
        validateWindow(from, until);
        l.setAvailableFrom(from);
        l.setAvailableUntil(until);
        if (r.unitWeightKg() != null) l.setUnitWeightKg(r.unitWeightKg());
        if (r.quantityAvailable() != null) l.restock(r.quantityAvailable());
        if (r.minOrderQuantity() != null) l.setMinOrderQuantity(r.minOrderQuantity());
        if (l.getMinOrderQuantity().compareTo(l.getQuantityTotal()) > 0) {
            throw ApiException.badRequest("Minimum order cannot be larger than the quantity offered");
        }
        if (r.pricePerUnit() != null) l.setPricePerUnit(r.pricePerUnit());
        if (r.organic() != null) l.setOrganic(r.organic());
        if (r.address() != null) l.setAddress(r.address().toEntity(regions));
        return l;
    }

    /** Farmer-driven transitions: publish, pause, resume, remove. */
    @Transactional
    public Listing changeStatusAsFarmer(UUID farmerId, UUID listingId, ListingStatus target) {
        Listing l = requireOwn(farmerId, listingId);
        if (l.getStatus() == ListingStatus.SUSPENDED) {
            throw ApiException.forbidden("This listing was suspended by AgriLink. Contact support.");
        }
        if (l.getStatus() == ListingStatus.REMOVED) {
            throw ApiException.conflict("Removed listings cannot be changed");
        }
        switch (target) {
            case ACTIVE -> l.publish(Instant.now(clock));
            case PAUSED -> {
                if (l.getStatus() != ListingStatus.ACTIVE && l.getStatus() != ListingStatus.SOLD_OUT) {
                    throw ApiException.invalidTransition("Only published listings can be paused");
                }
                l.setStatus(ListingStatus.PAUSED);
            }
            case DRAFT -> l.setStatus(ListingStatus.DRAFT);
            case REMOVED -> l.setStatus(ListingStatus.REMOVED);
            default -> throw ApiException.badRequest("Farmers can set ACTIVE, PAUSED, DRAFT or REMOVED");
        }
        return l;
    }

    @Transactional
    public Listing changeStatusAsAdmin(UUID listingId, ListingStatus target) {
        Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        if (!EnumSet.of(ListingStatus.SUSPENDED, ListingStatus.REMOVED, ListingStatus.ACTIVE, ListingStatus.PAUSED)
                .contains(target)) {
            throw ApiException.badRequest("Admins can set ACTIVE, PAUSED, SUSPENDED or REMOVED");
        }
        if (target == ListingStatus.ACTIVE) {
            l.publish(Instant.now(clock));
        } else {
            l.setStatus(target);
        }
        return l;
    }

    @Transactional
    public Listing addPhoto(UUID farmerId, UUID listingId, UUID fileId, boolean makePrimary) {
        Listing l = requireOwn(farmerId, listingId);
        files.requireOwned(fileId, farmerId, FilePurpose.LISTING_PHOTO);
        if (l.getPhotos().size() >= 8) {
            throw ApiException.badRequest("A listing can have at most 8 photos");
        }
        boolean primary = makePrimary || l.getPhotos().isEmpty();
        if (primary) {
            l.getPhotos().forEach(p -> p.setCover(false));
        }
        l.getPhotos().add(new ListingPhoto(l, fileId, l.getPhotos().size(), primary));
        return l;
    }

    @Transactional
    public Listing removePhoto(UUID farmerId, UUID listingId, UUID photoId) {
        Listing l = requireOwn(farmerId, listingId);
        boolean removed = l.getPhotos().removeIf(p -> p.getId().equals(photoId));
        if (!removed) {
            throw ApiException.notFound("Photo");
        }
        if (!l.getPhotos().isEmpty() && l.getPhotos().stream().noneMatch(ListingPhoto::isCover)) {
            l.getPhotos().get(0).setCover(true);
        }
        return l;
    }

    @Transactional(readOnly = true)
    public Page<Listing> searchMarketplace(ListingSearchCriteria criteria, String sort, Pageable pageable) {
        return listings.findAll(
                ListingSpecifications.marketplace(criteria, marketplaceProps.requireVerifiedFarmers(), today()),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), marketplaceSort(sort)));
    }

    @Transactional(readOnly = true)
    public Page<Listing> searchManagement(ListingSearchCriteria criteria, Pageable pageable) {
        return listings.findAll(ListingSpecifications.management(criteria),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** Detail view: public listings for everyone, any listing for its farmer or an admin. */
    @Transactional(readOnly = true)
    public Listing getVisible(UUID listingId, UUID viewerId, boolean admin) {
        Listing l = listings.findWithDetailsById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        boolean owner = viewerId != null && l.getFarmer().getId().equals(viewerId);
        if (!l.isPubliclyVisible() && !owner && !admin) {
            throw ApiException.notFound("Listing");
        }
        return l;
    }

    @Transactional(readOnly = true)
    public Listing getWithDetails(UUID listingId) {
        return listings.findWithDetailsById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
    }

    /** Moves ACTIVE/SOLD_OUT listings whose window ended to EXPIRED. Called by the scheduler. */
    @Transactional
    public int expireOverdue() {
        List<UUID> ids = listings.findIdsPastAvailability(
                EnumSet.of(ListingStatus.ACTIVE, ListingStatus.SOLD_OUT, ListingStatus.PAUSED), today());
        listings.findAllById(ids).forEach(l -> l.setStatus(ListingStatus.EXPIRED));
        return ids.size();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private void validateWindow(LocalDate from, LocalDate until) {
        if (until != null && until.isBefore(from)) {
            throw ApiException.badRequest("'Available until' cannot be before 'available from'");
        }
    }

    private BigDecimal resolveUnitWeight(Unit unit, BigDecimal supplied) {
        BigDecimal weight = supplied != null ? supplied : unit.defaultWeightKg();
        if (weight == null) {
            throw ApiException.badRequest("Tell us how many kg one " + unit.name().toLowerCase()
                    + " weighs (unitWeightKg) so delivery can be priced");
        }
        return weight;
    }

    private Address defaultAddress(UUID farmerId) {
        return farmerProfiles.findByUserId(farmerId).map(FarmerProfile::getAddress).map(Address::copy)
                .orElseGet(Address::new);
    }

    private void assertEditable(Listing l) {
        if (l.getStatus() == ListingStatus.REMOVED || l.getStatus() == ListingStatus.SUSPENDED
                || l.getStatus() == ListingStatus.EXPIRED) {
            throw ApiException.conflict("This listing can no longer be edited");
        }
    }

    private Listing requireOwn(UUID farmerId, UUID listingId) {
        Listing l = listings.findWithDetailsById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        if (!l.getFarmer().getId().equals(farmerId)) {
            throw ApiException.notFound("Listing");
        }
        return l;
    }

    private User requireFarmer(UUID farmerId) {
        User user = users.findById(farmerId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getRole() != Role.FARMER) {
            throw ApiException.forbidden("Only farmers can manage listings");
        }
        return user;
    }

    private static Sort marketplaceSort(String sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by("id"));
        }
        return switch (sort) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "pricePerUnit").and(Sort.by("id"));
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "pricePerUnit").and(Sort.by("id"));
            case "quantity_desc" -> Sort.by(Sort.Direction.DESC, "quantityAvailable").and(Sort.by("id"));
            case "available_soon" -> Sort.by(Sort.Direction.ASC, "availableFrom").and(Sort.by("id"));
            case "newest" -> Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by("id"));
            default -> throw ApiException.badRequest("Unknown sort: " + sort
                    + ". Use newest, price_asc, price_desc, quantity_desc or available_soon");
        };
    }
}
