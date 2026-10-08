package com.agrilink.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrilink.TestSupport;
import com.agrilink.buyer.BuyerProfileRepository;
import com.agrilink.common.AddressDto;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.marketplace.Listing;
import com.agrilink.marketplace.ListingRepository;
import com.agrilink.marketplace.ListingStatus;
import com.agrilink.marketplace.Product;
import com.agrilink.marketplace.ProductCategory;
import com.agrilink.marketplace.Unit;
import com.agrilink.order.OrderDtos.CreateOrderRequest;
import com.agrilink.order.OrderDtos.OrderItemRequest;
import com.agrilink.payment.PaymentService;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock ListingRepository listings;
    @Mock UserRepository users;
    @Mock BuyerProfileRepository buyerProfiles;
    @Mock PaymentService payments;
    @Mock RegionCatalog regions;
    @Mock ApplicationEventPublisher events;

    OrderService service;
    User buyer;
    User farmer;
    User driver;
    Listing listing;

    @BeforeEach
    void setUp() {
        var props = TestSupport.properties();
        service = new OrderService(orders, history, listings, users, buyerProfiles, new PricingService(props),
                payments, regions, events, props, TestSupport.clock());
        buyer = TestSupport.user(Role.BUYER, "+251922000001", "Selam");
        farmer = TestSupport.user(Role.FARMER, "+251911000001", "Tolosa");
        driver = TestSupport.user(Role.DRIVER, "+251933000001", "Abebe");
        listing = listing(farmer, "2400", "46.00");
        when(users.findById(buyer.getId())).thenReturn(Optional.of(buyer));
    }

    // ---------------------------------------------------------------- creation

    @Test
    void createReservesStockAndComputesTotalsLikeTheDesign() {
        when(orders.nextOrderSequence()).thenReturn(20481L);
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing));
        when(orders.save(any(Order.class))).thenAnswer(i -> TestSupport.withId(i.getArgument(0)));

        Order order = service.create(buyer.getId(), request(listing.getId(), "400"));

        assertThat(order.getOrderNumber()).isEqualTo("AL-20481");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getSubtotalAmount()).isEqualByComparingTo("18400.00");
        assertThat(order.getPlatformFee()).isEqualByComparingTo("368.00");
        assertThat(order.getTotalAmount())
                .isEqualByComparingTo(order.getSubtotalAmount().add(order.getDeliveryFee()).add(order.getPlatformFee()));
        assertThat(order.getTotalWeightKg()).isEqualByComparingTo("400");
        assertThat(order.getFarmerResponseDeadline()).isEqualTo(TestSupport.NOW.plus(Duration.ofHours(2)));
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("2000");
        ArgumentCaptor<OrderStatusChangedEvent> event = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().to()).isEqualTo(OrderStatus.PENDING);
        assertThat(event.getValue().farmerId()).isEqualTo(farmer.getId());
    }

    @Test
    void createRejectsBasketsFromTwoFarmers() {
        User other = TestSupport.user(Role.FARMER, "+251911000002", "Ayantu");
        Listing second = listing(other, "900", "31.50");
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing, second));

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(listing.getId(), new BigDecimal("100")),
                new OrderItemRequest(second.getId(), new BigDecimal("100"))), address(), null, null, null, null);

        assertThatThrownBy(() -> service.create(buyer.getId(), request)).isInstanceOf(ApiException.class)
                .hasMessageContaining("one farmer");
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("2400");
    }

    @Test
    void createRejectsOrderingOwnProduce() {
        farmer = TestSupport.user(Role.FARMER, "+251911000009", "Both");
        listing = listing(farmer, "100", "10");
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing));
        when(users.findById(farmer.getId())).thenReturn(Optional.of(
                TestSupport.withId(new User("+251911000009", "Both", Role.BUYER), farmer.getId())));

        assertThatThrownBy(() -> service.create(farmer.getId(), request(listing.getId(), "10")))
                .isInstanceOf(ApiException.class).hasMessageContaining("own produce");
    }

    @Test
    void createRefusesNonBuyers() {
        when(users.findById(farmer.getId())).thenReturn(Optional.of(farmer));
        assertThatThrownBy(() -> service.create(farmer.getId(), request(listing.getId(), "100")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    // ---------------------------------------------------------------- farmer answers

    @Test
    void acceptMovesToAcceptedAndStartsPaymentClock() {
        Order order = order(OrderStatus.PENDING);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));

        service.accept(farmer.getId(), order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getPaymentDeadline()).isEqualTo(TestSupport.NOW.plus(Duration.ofMinutes(30)));
        verify(history).save(any(OrderStatusHistory.class));
    }

    @Test
    void anotherFarmerCannotAcceptSomeoneElsesOrder() {
        Order order = order(OrderStatus.PENDING);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        UUID stranger = UUID.randomUUID();

        assertThatThrownBy(() -> service.accept(stranger, order.getId())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void acceptAfterTheResponseWindowIsRefused() {
        Order order = order(OrderStatus.PENDING);
        order.setFarmerResponseDeadline(TestSupport.NOW.minusSeconds(1));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.accept(farmer.getId(), order.getId())).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectReturnsReservedStock() {
        Order order = order(OrderStatus.PENDING);
        listing.reserve(new BigDecimal("400"));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing));

        service.reject(farmer.getId(), order.getId(), "Out of stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("2400");
    }

    @Test
    void cannotMarkReadyBeforePayment() {
        Order order = order(OrderStatus.ACCEPTED);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.markReady(farmer.getId(), order.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    // ---------------------------------------------------------------- payment hooks

    @Test
    void paymentLifecycleDrivesOrderStatus() {
        Order order = order(OrderStatus.ACCEPTED);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));

        service.onPaymentInitiated(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        service.onPaymentFailed(order.getId(), "insufficient funds");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        service.onPaymentInitiated(order.getId());
        service.onPaymentHeld(order.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // ---------------------------------------------------------------- cancellation

    @Test
    void buyerCancellingAPaidOrderRefundsAndReturnsStock() {
        Order order = order(OrderStatus.PAID);
        listing.reserve(new BigDecimal("400"));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing));

        service.cancel(buyer.getId(), Role.BUYER, order.getId(), "Changed my mind");

        verify(payments).refundHeld(eq(order), anyString());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("2400");
    }

    @Test
    void cancellingAnUnpaidOrderCancelsThePendingPaymentWithoutRefund() {
        Order order = order(OrderStatus.PAYMENT_PENDING);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing));

        service.cancel(buyer.getId(), Role.BUYER, order.getId(), "No longer needed");

        verify(payments).cancelPending(eq(order), anyString());
        verify(payments, never()).refundHeld(any(), anyString());
    }

    @Test
    void buyerCannotCancelOnceTheGoodsAreReadyForPickup() {
        Order order = order(OrderStatus.READY_FOR_PICKUP);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(buyer.getId(), Role.BUYER, order.getId(), "x"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        verify(payments, never()).refundHeld(any(), anyString());
    }

    @Test
    void outsiderCannotCancel() {
        Order order = order(OrderStatus.PENDING);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), Role.BUYER, order.getId(), "x"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ---------------------------------------------------------------- completion

    @Test
    void buyerConfirmationCompletesTheOrderAndReleasesEscrow() {
        Order order = order(OrderStatus.DELIVERED);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));

        service.confirmDelivery(buyer.getId(), order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(payments).releaseEscrow(order);
    }

    @Test
    void onlyTheBuyerCanConfirm() {
        Order order = order(OrderStatus.DELIVERED);
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.confirmDelivery(farmer.getId(), order.getId()))
                .isInstanceOf(ApiException.class);
        verify(payments, never()).releaseEscrow(any());
    }

    @Test
    void autoCompleteWaitsForTheCheckWindow() {
        Order order = order(OrderStatus.DELIVERED);
        order.setCheckWindowEndsAt(TestSupport.NOW.plusSeconds(60));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        assertThat(service.autoComplete(order.getId())).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        order.setCheckWindowEndsAt(TestSupport.NOW.minusSeconds(1));
        assertThat(service.autoComplete(order.getId())).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(payments).releaseEscrow(order);
    }

    @Test
    void unpaidOrdersExpireAndGiveStockBack() {
        Order order = order(OrderStatus.ACCEPTED);
        order.setPaymentDeadline(TestSupport.NOW.minusSeconds(5));
        listing.reserve(new BigDecimal("400"));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing));

        assertThat(service.expireUnpaid(order.getId())).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(listing.getQuantityAvailable()).isEqualByComparingTo("2400");
        verify(payments).cancelPending(eq(order), anyString());
    }

    @Test
    void pendingOrdersExpireWhenTheFarmerDoesNotAnswer() {
        Order order = order(OrderStatus.PENDING);
        order.setFarmerResponseDeadline(TestSupport.NOW.minusSeconds(1));
        when(orders.lockById(order.getId())).thenReturn(Optional.of(order));
        when(listings.lockAllByIdIn(any())).thenReturn(List.of(listing));

        assertThat(service.expirePending(order.getId())).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    // ---------------------------------------------------------------- helpers

    private Order order(OrderStatus status) {
        Order o = TestSupport.withId(new Order("AL-20481", buyer, farmer));
        o.setSubtotalAmount(new BigDecimal("18400.00"));
        o.setDeliveryFee(new BigDecimal("3200.00"));
        o.setPlatformFee(new BigDecimal("368.00"));
        o.setTotalAmount(new BigDecimal("21968.00"));
        o.setTotalWeightKg(new BigDecimal("400"));
        o.addItem(new OrderItem(o, listing.getId(), UUID.randomUUID(), "Tomato A", "A", Unit.KG,
                new BigDecimal("400"), new BigDecimal("46.00"), BigDecimal.ONE, new BigDecimal("18400.00")));
        if (status != OrderStatus.PENDING) {
            o.applyStatus(status, TestSupport.NOW, null);
        }
        if (status == OrderStatus.PENDING) {
            o.setFarmerResponseDeadline(TestSupport.NOW.plusSeconds(3600));
        }
        o.setDriver(driver);
        return o;
    }

    private Listing listing(User owner, String quantity, String price) {
        var category = new ProductCategory("vegetables", "Vegetables", null, null, null, 1);
        var product = TestSupport.withId(new Product(category, "tomato", "Tomato", null, null, Unit.KG, null));
        Listing l = TestSupport.withId(new Listing(owner, product));
        l.setTitle("Tomato A");
        l.setUnit(Unit.KG);
        l.setUnitWeightKg(BigDecimal.ONE);
        l.setQuantityTotal(new BigDecimal(quantity));
        l.setQuantityAvailable(new BigDecimal(quantity));
        l.setMinOrderQuantity(new BigDecimal("10"));
        l.setPricePerUnit(new BigDecimal(price));
        l.setAvailableFrom(LocalDate.now());
        l.publish(TestSupport.NOW);
        assertThat(l.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        return l;
    }

    private static AddressDto address() {
        return new AddressDto(null, null, "Bole", null, "Addis Ababa", "Behind Edna Mall", 8.99, 38.79);
    }

    private CreateOrderRequest request(UUID listingId, String quantity) {
        return new CreateOrderRequest(List.of(new OrderItemRequest(listingId, new BigDecimal(quantity))), address(),
                null, null, null, "Please deliver before noon");
    }
}
