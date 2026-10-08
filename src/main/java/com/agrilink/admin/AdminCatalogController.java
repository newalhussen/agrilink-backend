package com.agrilink.admin;

import com.agrilink.common.ApiException;
import com.agrilink.common.PageResponse;
import com.agrilink.marketplace.CatalogDtos.CategoryRequest;
import com.agrilink.marketplace.CatalogDtos.CategoryResponse;
import com.agrilink.marketplace.CatalogDtos.ProductRequest;
import com.agrilink.marketplace.CatalogDtos.ProductResponse;
import com.agrilink.marketplace.CatalogService;
import com.agrilink.marketplace.Listing;
import com.agrilink.marketplace.ListingDtos.ChangeStatusRequest;
import com.agrilink.marketplace.ListingDtos.ListingResponse;
import com.agrilink.marketplace.ListingMapper;
import com.agrilink.marketplace.ListingSearchCriteria;
import com.agrilink.marketplace.ListingService;
import com.agrilink.marketplace.ListingStatus;
import com.agrilink.marketplace.ProductCategoryRepository;
import com.agrilink.marketplace.ProductRepository;
import com.agrilink.marketplace.QualityGrade;
import com.agrilink.security.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
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

/** Catalogue (categories and products) and listing moderation. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final CatalogService catalog;
    private final ProductCategoryRepository categories;
    private final ProductRepository products;
    private final ListingService listings;
    private final ListingMapper listingMapper;
    private final AdminAuditService audit;

    public AdminCatalogController(CatalogService catalog, ProductCategoryRepository categories,
                                  ProductRepository products, ListingService listings, ListingMapper listingMapper,
                                  AdminAuditService audit) {
        this.catalog = catalog;
        this.categories = categories;
        this.products = products;
        this.listings = listings;
        this.listingMapper = listingMapper;
        this.audit = audit;
    }

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAll(Sort.by("sortOrder", "nameEn")).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        var c = catalog.createCategory(request);
        audit.record(AuthContext.userId(), "CATEGORY_CREATED", "CATEGORY", c.getId(), c.getSlug());
        return CategoryResponse.from(c);
    }

    @PutMapping("/categories/{id}")
    @Transactional
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        var c = catalog.updateCategory(id, request);
        audit.record(AuthContext.userId(), "CATEGORY_UPDATED", "CATEGORY", id, c.getSlug());
        return CategoryResponse.from(c);
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductResponse> products(@RequestParam(required = false) UUID categoryId) {
        return products.findAll(Sort.by("nameEn")).stream()
                .filter(p -> categoryId == null || p.getCategory().getId().equals(categoryId))
                .map(ProductResponse::from).toList();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        var p = catalog.createProduct(request);
        audit.record(AuthContext.userId(), "PRODUCT_CREATED", "PRODUCT", p.getId(), p.getSlug());
        return ProductResponse.from(p);
    }

    @PutMapping("/products/{id}")
    @Transactional
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        var p = catalog.updateProduct(id, request);
        audit.record(AuthContext.userId(), "PRODUCT_UPDATED", "PRODUCT", id, p.getSlug());
        return ProductResponse.from(p);
    }

    @GetMapping("/listings")
    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> listings(@RequestParam(required = false) ListingStatus status,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) UUID categoryId,
                                                  @RequestParam(required = false) UUID productId,
                                                  @RequestParam(required = false) UUID regionId,
                                                  @RequestParam(required = false) UUID farmerId,
                                                  @RequestParam(required = false) QualityGrade grade,
                                                  Pageable pageable) {
        ListingSearchCriteria criteria = new ListingSearchCriteria(q, categoryId, productId, regionId, farmerId, null,
                null, grade, null, null, null, status);
        var page = listings.searchManagement(criteria, pageable);
        return new PageResponse<>(listingMapper.toResponses(page.getContent(), null, null), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    @GetMapping("/listings/{id}")
    @Transactional(readOnly = true)
    public ListingResponse listing(@PathVariable UUID id) {
        return listingMapper.toResponse(listings.getWithDetails(id), null, null);
    }

    /** Suspend, remove, pause or reinstate a listing. */
    @PatchMapping("/listings/{id}/status")
    @Transactional
    public ListingResponse changeListingStatus(@PathVariable UUID id,
                                               @Valid @RequestBody ChangeStatusRequest request) {
        Listing l = listings.changeStatusAsAdmin(id, request.status());
        audit.record(AuthContext.userId(), "LISTING_" + request.status(), "LISTING", id, l.getTitle());
        return listingMapper.toResponse(l, null, null);
    }
}
