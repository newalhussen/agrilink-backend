package com.agrilink.marketplace;

import com.agrilink.common.ApiException;
import com.agrilink.marketplace.CatalogDtos.CategoryRequest;
import com.agrilink.marketplace.CatalogDtos.ProductRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final ProductCategoryRepository categories;
    private final ProductRepository products;

    public CatalogService(ProductCategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<ProductCategory> listCategories() {
        return categories.findByActiveTrueOrderBySortOrderAscNameEnAsc();
    }

    @Transactional(readOnly = true)
    public List<Product> searchProducts(UUID categoryId, String q) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return products.search(categoryId, pattern, Sort.by("nameEn"));
    }

    @Transactional(readOnly = true)
    public Product requireActiveProduct(UUID productId) {
        Product product = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product"));
        if (!product.isActive()) {
            throw ApiException.badRequest("This product is no longer available in the catalogue");
        }
        return product;
    }

    @Transactional
    public ProductCategory createCategory(CategoryRequest r) {
        if (categories.existsBySlug(r.slug())) {
            throw ApiException.conflict("A category with this slug already exists");
        }
        ProductCategory category = new ProductCategory(r.slug(), r.nameEn(), r.nameAm(), r.nameOm(), r.icon(),
                r.sortOrder() == null ? 0 : r.sortOrder());
        return categories.save(category);
    }

    @Transactional
    public ProductCategory updateCategory(UUID id, CategoryRequest r) {
        ProductCategory category = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        category.update(r.nameEn(), r.nameAm(), r.nameOm(), r.icon(),
                r.sortOrder() == null ? category.getSortOrder() : r.sortOrder(),
                r.active() == null ? category.isActive() : r.active());
        return category;
    }

    @Transactional
    public Product createProduct(ProductRequest r) {
        if (products.existsBySlug(r.slug())) {
            throw ApiException.conflict("A product with this slug already exists");
        }
        ProductCategory category = categories.findById(r.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Unknown category"));
        return products.save(new Product(category, r.slug(), r.nameEn(), r.nameAm(), r.nameOm(), r.defaultUnit(),
                r.description()));
    }

    @Transactional
    public Product updateProduct(UUID id, ProductRequest r) {
        Product product = products.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
        ProductCategory category = categories.findById(r.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Unknown category"));
        product.update(category, r.nameEn(), r.nameAm(), r.nameOm(), r.defaultUnit(), r.description(),
                r.active() == null ? product.isActive() : r.active());
        return product;
    }
}
