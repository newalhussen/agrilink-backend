package com.agrilink.marketplace;

import com.agrilink.marketplace.CatalogDtos.CategoryResponse;
import com.agrilink.marketplace.CatalogDtos.ProductResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalogue browsing. Admin maintenance lives under /api/v1/admin. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.listCategories().stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    public List<ProductResponse> products(@RequestParam(required = false) UUID categoryId,
                                          @RequestParam(required = false) String q) {
        return catalog.searchProducts(categoryId, q).stream().map(ProductResponse::from).toList();
    }
}
