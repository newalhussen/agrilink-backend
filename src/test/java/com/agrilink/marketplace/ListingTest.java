package com.agrilink.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrilink.TestSupport;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.user.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListingTest {

    private Listing listing;

    @BeforeEach
    void setUp() {
        var category = new ProductCategory("vegetables", "Vegetables", null, null, null, 1);
        var product = new Product(category, "tomato", "Tomato", null, null, Unit.KG, null);
        listing = new Listing(TestSupport.user(Role.FARMER, "+251911000001", "Tolosa"), product);
        listing.setTitle("Tomato A");
        listing.setUnit(Unit.KG);
        listing.setUnitWeightKg(BigDecimal.ONE);
        listing.setQuantityTotal(new BigDecimal("500"));
        listing.setQuantityAvailable(new BigDecimal("500"));
        listing.setMinOrderQuantity(new BigDecimal("100"));
        listing.setPricePerUnit(new BigDecimal("46"));
        listing.setAvailableFrom(LocalDate.now());
        listing.publish(TestSupport.NOW);
    }

    @Test
    void reserveReducesStockAndSellsOut() {
        listing.reserve(new BigDecimal("400"));
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("100");
        listing.reserve(new BigDecimal("100"));
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
    }

    @Test
    void releaseRestoresStockAndReopensSoldOutListing() {
        listing.reserve(new BigDecimal("500"));
        listing.release(new BigDecimal("500"));
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("500");
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void cannotOrderMoreThanAvailable() {
        assertThatThrownBy(() -> listing.reserve(new BigDecimal("501")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    @Test
    void cannotOrderBelowMinimum() {
        assertThatThrownBy(() -> listing.reserve(new BigDecimal("50")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).contains("Minimum order"));
    }

    @Test
    void pausedListingCannotBeOrdered() {
        listing.setStatus(ListingStatus.PAUSED);
        assertThatThrownBy(() -> listing.reserve(new BigDecimal("100")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.LISTING_UNAVAILABLE));
    }

    @Test
    void releaseNeverExceedsTotal() {
        listing.release(new BigDecimal("100"));
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("500");
    }

    @Test
    void restockKeepsTotalConsistent() {
        listing.reserve(new BigDecimal("200"));
        listing.restock(new BigDecimal("700"));
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("700");
        assertThat(listing.getQuantityTotal()).isEqualByComparingTo("900");
    }
}
