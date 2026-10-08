package com.agrilink.order;

import com.agrilink.buyer.BuyerProfile;
import com.agrilink.buyer.BuyerProfileRepository;
import com.agrilink.common.Address;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.common.Money;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.marketplace.Listing;
import com.agrilink.marketplace.ListingRepository;
import com.agrilink.order.OrderDtos.CreateOrderRequest;
import com.agrilink.order.OrderDtos.OrderItemRequest;
import com.agrilink.order.OrderDtos.QuoteLine;
import com.agrilink.order.OrderDtos.QuoteResponse;
import com.agrilink.order.PricingService.DeliveryQuote;
import com.agrilink.payment.PaymentService;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.AccountStatus;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle. Every state change goes through {@link #transition}, which enforces the state machine,
 * records history and publishes an {@link OrderStatusChangedEvent} that delivery, notifications and
 * statistics react to. Rows are locked (order first, then payment) so concurrent requests serialise.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ListingRepository listings;
    private final UserRepository users;
    private final BuyerProfileRepository buyerProfiles;
    private final PricingService pricing;
    private final PaymentService payments;
    private final RegionCatalog regions;
    private final ApplicationEventPublisher events;
    private final AgriLinkProperties.Orders props;
    private final Clock clock;

    public OrderService(OrderRepository orders, OrderStatusHistoryRepository history, ListingRepository listings,
                        UserRepository users, BuyerProfileRepository buyerProfiles, PricingService pricing,
                        PaymentService payments, RegionCatalog regions, ApplicationEventPublisher events,
                        AgriLinkProperties properties, Clock clock) {
        this.orders = orders;
        this.history = history;
        this.listings = listings;
        this.users = users;
        this.buyerProfiles = buyerProfiles;
        this.pricing = pricing;
        this.payments = payments;
        this.regions = regions;
        this.events = events;
        this.props = properties.orders();
        this.clock = clock;
    }

    // ------------------------------------------------------------------ creation

    /** Prices a basket without reserving stock. */
    @Transactional(readOnly = true)
    public QuoteResponse quote(UUID buyerId, CreateOrderRequest request) {
        User buyer = requireBuyer(buyerId);
        Draft draft = prepare(buyer, request, false);
        return draft.toQuote();
    }

    @Transactional
    public Order create(UUID buyerId, CreateOrderRequest request) {
        User buyer = requireBuyer(buyerId);
        Draft draft = prepare(buyer, request, true);
        Instant now = Instant.now(clock);

        String number = "AL-" + orders.nextOrderSequence();
        Order order = new Order(number, buyer, draft.farmer);
        for (Draft.Line line : draft.lines) {
            Listing l = line.listing;
            BigDecimal lineTotal = Money.scale(l.getPricePerUnit().multiply(line.quantity));
            order.addItem(new OrderItem(order, l.getId(), l.getProduct().getId(), l.getTitle(),
                    l.getQualityGrade().name(), l.getUnit(), line.quantity, l.getPricePerUnit(), l.getUnitWeightKg(),
                    lineTotal));
        }
        order.setSubtotalAmount(draft.subtotal);
        order.setDeliveryFee(draft.deliveryFee);
        order.setPlatformFee(draft.platformFee);
        order.setTotalAmount(draft.total);
        order.setTotalWeightKg(draft.weightKg);
        order.setDeliveryAddress(draft.deliveryAddress);
        order.setDeliveryContactName(draft.contactName);
        order.setDeliveryContactPhone(draft.contactPhone);
        order.setRequestedDeliveryDate(request.requestedDeliveryDate());
        order.setBuyerNotes(request.notes());
        order.setFarmerResponseDeadline(now.plus(props.farmerResponseWindow()));
        orders.save(order);

        history.save(new OrderStatusHistory(order.getId(), null, OrderStatus.PENDING, buyerId, Role.BUYER,
                "Order placed", now));
        publish(order, null, OrderStatus.PENDING, buyerId, Role.BUYER, null);
        return order;
    }

    // ------------------------------------------------------------------ farmer / buyer actions

    @Transactional
    public Order accept(UUID farmerId, UUID orderId) {
        Order order = lockOwnedByFarmer(farmerId, orderId);
        requireStatus(order, OrderStatus.PENDING);
        Instant now = Instant.now(clock);
        if (order.getFarmerResponseDeadline() != null && !order.getFarmerResponseDeadline().isAfter(now)) {
            throw ApiException.conflict("The time to answer this order has passed");
        }
        order.setPaymentDeadline(now.plus(props.paymentWindow()));
        transition(order, OrderStatus.ACCEPTED, farmerId, Role.FARMER, null);
        return order;
    }

    @Transactional
    public Order reject(UUID farmerId, UUID orderId, String reason) {
        Order order = lockOwnedByFarmer(farmerId, orderId);
        requireStatus(order, OrderStatus.PENDING);
        releaseReservations(order);
        transition(order, OrderStatus.REJECTED, farmerId, Role.FARMER, reason);
        return order;
    }

    @Transactional
    public Order markReady(UUID farmerId, UUID orderId) {
        Order order = lockOwnedByFarmer(farmerId, orderId);
        requireStatus(order, OrderStatus.PAID);
        transition(order, OrderStatus.READY_FOR_PICKUP, farmerId, Role.FARMER, null);
        return order;
    }

    @Transactional
    public Order confirmDelivery(UUID buyerId, UUID orderId) {
        Order order = lock(orderId);
        if (!order.getBuyer().getId().equals(buyerId)) {
            throw ApiException.notFound("Order");
        }
        requireStatus(order, OrderStatus.DELIVERED);
        complete(order, buyerId, Role.BUYER, "Buyer confirmed delivery");
        return order;
    }

    /**
     * Cancels before pickup. Buyers may cancel until the goods are packed for pickup, farmers while they have
     * not been collected, admins at any non-final stage. Money already held is refunded in full.
     */
    @Transactional
    public Order cancel(UUID actorId, Role actorRole, UUID orderId, String reason) {
        Order order = lock(orderId);
        OrderStatus status = order.getStatus();
        boolean allowed = switch (actorRole) {
            case BUYER -> order.getBuyer().getId().equals(actorId)
                    && EnumSet.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.PAYMENT_PENDING,
                    OrderStatus.PAID).contains(status);
            case FARMER -> order.getFarmer().getId().equals(actorId)
                    && EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID,
                    OrderStatus.READY_FOR_PICKUP).contains(status);
            case ADMIN -> EnumSet.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.PAYMENT_PENDING,
                    OrderStatus.PAID, OrderStatus.READY_FOR_PICKUP).contains(status);
            case DRIVER -> false;
        };
        if (!allowed) {
            if (order.isParticipant(actorId) || actorRole == Role.ADMIN) {
                throw ApiException.invalidTransition("An order in status " + status + " cannot be cancelled by you."
                        + " Report a problem instead.");
            }
            throw ApiException.notFound("Order");
        }
        if (status == OrderStatus.ACCEPTED || status == OrderStatus.PAYMENT_PENDING) {
            payments.cancelPending(order, reason);
        } else if (status.isFunded()) {
            payments.refundHeld(order, "Order cancelled: " + reason);
        }
        if (status.isBeforePickup()) {
            releaseReservations(order);
        }
        transition(order, OrderStatus.CANCELLED, actorId, actorRole, reason);
        return order;
    }

    // ------------------------------------------------------------------ hooks used by other modules

    /** Locks and returns the order row; the lock lasts until the surrounding transaction ends. */
    @Transactional
    public Order lock(UUID orderId) {
        return orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
    }

    @Transactional
    public void transition(Order order, OrderStatus to, UUID actorId, Role actorRole, String note) {
        OrderStatus from = order.getStatus();
        OrderStateMachine.assertTransition(from, to);
        Instant now = Instant.now(clock);
        order.applyStatus(to, now, note);
        history.save(new OrderStatusHistory(order.getId(), from, to, actorId, actorRole, note, now));
        publish(order, from, to, actorId, actorRole, note);
    }

    @Transactional
    public void onPaymentInitiated(UUID orderId) {
        Order order = lock(orderId);
        if (order.getStatus() == OrderStatus.ACCEPTED) {
            transition(order, OrderStatus.PAYMENT_PENDING, order.getBuyer().getId(), Role.BUYER, "Payment started");
        }
    }

    @Transactional
    public void onPaymentFailed(UUID orderId, String reason) {
        Order order = lock(orderId);
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            transition(order, OrderStatus.ACCEPTED, null, null, "Payment failed: " + reason);
        }
    }

    @Transactional
    public void onPaymentHeld(UUID orderId) {
        Order order = lock(orderId);
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            transition(order, OrderStatus.PAID, null, null, "Payment received");
        } else if (order.getStatus() == OrderStatus.ACCEPTED) {
            // Provider confirmed after we had already rolled the order back to ACCEPTED.
            transition(order, OrderStatus.PAYMENT_PENDING, null, null, "Payment confirmed late");
            transition(order, OrderStatus.PAID, null, null, "Payment received");
        }
    }

    @Transactional
    public void assignDriver(Order order, User driver) {
        order.setDriver(driver);
    }

    @Transactional
    public void clearDriver(Order order) {
        order.setDriver(null);
    }

    /** Driver scanned the buyer's code: starts the buyer's check window. */
    @Transactional
    public void markDelivered(Order order, UUID driverId) {
        order.setCheckWindowEndsAt(Instant.now(clock).plus(props.checkWindow()));
        transition(order, OrderStatus.DELIVERED, driverId, Role.DRIVER, "Delivered and code verified");
    }

    @Transactional
    public void complete(Order order, UUID actorId, Role actorRole, String note) {
        transition(order, OrderStatus.COMPLETED, actorId, actorRole, note);
        payments.releaseEscrow(order);
    }

    @Transactional
    public void markDisputed(Order order, UUID actorId, Role actorRole, String note) {
        order.setStatusBeforeDispute(order.getStatus());
        transition(order, OrderStatus.DISPUTED, actorId, actorRole, note);
    }

    /**
     * Closes a disputed order after the payment has been settled by the dispute workflow. Stock goes back on
     * sale only when the order is cancelled and the goods never left the farm.
     */
    @Transactional
    public void finishDispute(Order order, boolean completed, UUID adminId, String note) {
        if (!completed) {
            OrderStatus before = order.getStatusBeforeDispute();
            if (before != null && before.isBeforePickup()) {
                releaseReservations(order);
            }
        }
        transition(order, completed ? OrderStatus.COMPLETED : OrderStatus.CANCELLED, adminId, Role.ADMIN, note);
    }

    // ------------------------------------------------------------------ scheduled maintenance

    @Transactional
    public boolean expirePending(UUID orderId) {
        Order order = lock(orderId);
        if (order.getStatus() != OrderStatus.PENDING || order.getFarmerResponseDeadline() == null
                || order.getFarmerResponseDeadline().isAfter(Instant.now(clock))) {
            return false;
        }
        releaseReservations(order);
        transition(order, OrderStatus.EXPIRED, null, null, "The farmer did not respond in time");
        return true;
    }

    @Transactional
    public boolean expireUnpaid(UUID orderId) {
        Order order = lock(orderId);
        boolean unpaid = order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.PAYMENT_PENDING;
        if (!unpaid || order.getPaymentDeadline() == null || order.getPaymentDeadline().isAfter(Instant.now(clock))) {
            return false;
        }
        payments.cancelPending(order, "Payment window closed");
        releaseReservations(order);
        transition(order, OrderStatus.EXPIRED, null, null, "Payment was not completed in time");
        return true;
    }

    @Transactional
    public boolean autoComplete(UUID orderId) {
        Order order = lock(orderId);
        if (order.getStatus() != OrderStatus.DELIVERED || order.getCheckWindowEndsAt() == null
                || order.getCheckWindowEndsAt().isAfter(Instant.now(clock))) {
            return false;
        }
        complete(order, null, null, "Check window elapsed without a problem report");
        return true;
    }

    // ------------------------------------------------------------------ queries

    /** Loads an order the user takes part in (or any order for admins); everyone else gets a 404. */
    @Transactional(readOnly = true)
    public Order getForUser(UUID orderId, UUID userId, Role role) {
        Order order = orders.findWithDetailsById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (role != Role.ADMIN && !order.isParticipant(userId)) {
            throw ApiException.notFound("Order");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public Page<Order> listFor(UUID userId, Role role, Collection<OrderStatus> statuses, Instant from, Instant to,
                               Pageable pageable) {
        var spec = OrderSpecifications.forParticipant(userId, role)
                .and(OrderSpecifications.withFilters(statuses, from, to, null, null, null, null));
        return orders.findAll(spec, newestFirst(pageable));
    }

    @Transactional(readOnly = true)
    public Page<Order> adminSearch(Collection<OrderStatus> statuses, Instant from, Instant to, UUID buyerId,
                                   UUID farmerId, UUID driverId, String q, Pageable pageable) {
        return orders.findAll(OrderSpecifications.withFilters(statuses, from, to, buyerId, farmerId, driverId, q),
                newestFirst(pageable));
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistory> timeline(UUID orderId) {
        return history.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    // ------------------------------------------------------------------ internals

    private void publish(Order order, OrderStatus from, OrderStatus to, UUID actorId, Role actorRole, String reason) {
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getOrderNumber(),
                order.getBuyer().getId(), order.getFarmer().getId(),
                order.getDriver() == null ? null : order.getDriver().getId(), from, to, actorId, actorRole, reason));
    }

    private void releaseReservations(Order order) {
        List<UUID> ids = order.getItems().stream().map(OrderItem::getListingId).distinct().toList();
        Map<UUID, Listing> byId = listings.lockAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        for (OrderItem item : order.getItems()) {
            Listing l = byId.get(item.getListingId());
            if (l != null) {
                l.release(item.getQuantity());
            }
        }
    }

    private void requireStatus(Order order, OrderStatus expected) {
        if (order.getStatus() != expected) {
            throw ApiException.invalidTransition("Order is " + order.getStatus() + ", expected " + expected);
        }
    }

    private Order lockOwnedByFarmer(UUID farmerId, UUID orderId) {
        Order order = lock(orderId);
        if (!order.getFarmer().getId().equals(farmerId)) {
            throw ApiException.notFound("Order");
        }
        return order;
    }

    private User requireBuyer(UUID buyerId) {
        User buyer = users.findById(buyerId).orElseThrow(() -> ApiException.notFound("User"));
        if (buyer.getRole() != Role.BUYER) {
            throw ApiException.forbidden("Only buyers can place orders");
        }
        if (buyer.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "Your account cannot place orders right now");
        }
        return buyer;
    }

    private static Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    /** Validated basket with computed amounts. Listings are locked and reserved only when {@code reserve} is set. */
    private Draft prepare(User buyer, CreateOrderRequest request, boolean reserve) {
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.listingId(), item.quantity(), BigDecimal::add);
        }
        List<UUID> ids = new ArrayList<>(quantities.keySet());
        List<Listing> found = reserve ? listings.lockAllByIdIn(ids) : listings.findAllById(ids);
        Map<UUID, Listing> byId = found.stream().collect(Collectors.toMap(Listing::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw ApiException.notFound("One of the listings");
        }
        Draft draft = new Draft();
        for (UUID id : ids) {
            Listing l = byId.get(id);
            User farmer = l.getFarmer();
            if (draft.farmer == null) {
                draft.farmer = farmer;
            } else if (!draft.farmer.getId().equals(farmer.getId())) {
                throw ApiException.badRequest(
                        "An order can only contain products from one farmer. Place a separate order for each farmer.");
            }
            if (farmer.getAccountStatus() != AccountStatus.ACTIVE) {
                throw new ApiException(ErrorCode.LISTING_UNAVAILABLE, "This farmer is not accepting orders right now");
            }
            if (farmer.getId().equals(buyer.getId())) {
                throw ApiException.badRequest("You cannot order your own produce");
            }
            BigDecimal quantity = quantities.get(id);
            l.assertOrderable(quantity);
            draft.lines.add(new Draft.Line(l, quantity));
        }
        if (reserve) {
            // Everything checked out: only now take the stock, so a rejected basket never touches inventory.
            draft.lines.forEach(line -> line.listing.reserve(line.quantity));
        }

        Address delivery = request.deliveryAddress().toEntity(regions);
        if (delivery.getRegionId() == null && !delivery.hasCoordinates()) {
            throw ApiException.badRequest("Delivery address needs a region or a map location");
        }
        draft.deliveryAddress = delivery;
        draft.contactName = request.deliveryContactName() != null && !request.deliveryContactName().isBlank()
                ? request.deliveryContactName().trim() : buyer.getFullName();
        draft.contactPhone = request.deliveryContactPhone() != null && !request.deliveryContactPhone().isBlank()
                ? com.agrilink.common.PhoneNumbers.normalize(request.deliveryContactPhone()) : buyer.getPhone();

        BigDecimal subtotal = Money.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        for (Draft.Line line : draft.lines) {
            subtotal = subtotal.add(Money.scale(line.listing.getPricePerUnit().multiply(line.quantity)));
            weight = weight.add(line.quantity.multiply(line.listing.getUnitWeightKg()));
        }
        draft.subtotal = Money.scale(subtotal);
        draft.weightKg = weight;
        Address pickup = draft.lines.get(0).listing.getAddress();
        DeliveryQuote quote = pricing.deliveryQuote(pickup, delivery, weight);
        draft.deliveryFee = quote.fee();
        draft.distanceKm = quote.distanceKm();
        draft.platformFee = pricing.platformFee(draft.subtotal);
        draft.total = draft.subtotal.add(draft.deliveryFee).add(draft.platformFee);
        return draft;
    }

    private static final class Draft {
        record Line(Listing listing, BigDecimal quantity) {}

        final List<Line> lines = new ArrayList<>();
        User farmer;
        Address deliveryAddress;
        String contactName;
        String contactPhone;
        BigDecimal subtotal;
        BigDecimal weightKg;
        BigDecimal deliveryFee;
        BigDecimal platformFee;
        BigDecimal total;
        BigDecimal distanceKm;

        QuoteResponse toQuote() {
            List<QuoteLine> quoteLines = lines.stream().map(l -> new QuoteLine(l.listing.getId(), l.listing.getTitle(),
                    l.listing.getUnit(), l.quantity, l.listing.getPricePerUnit(),
                    Money.scale(l.listing.getPricePerUnit().multiply(l.quantity)),
                    l.quantity.multiply(l.listing.getUnitWeightKg()))).toList();
            return new QuoteResponse(quoteLines, subtotal, deliveryFee, platformFee, total, Money.CURRENCY, weightKg,
                    distanceKm, farmer.getId());
        }
    }

    /** Statuses in which an order still counts as active work for its parties. */
    public static Set<OrderStatus> activeStatuses() {
        Set<OrderStatus> all = EnumSet.allOf(OrderStatus.class);
        all.removeIf(OrderStatus::isTerminal);
        return all;
    }
}
