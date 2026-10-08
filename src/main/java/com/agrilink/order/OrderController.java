package com.agrilink.order;

import com.agrilink.common.PageResponse;
import com.agrilink.order.OrderDtos.CreateOrderRequest;
import com.agrilink.order.OrderDtos.OrderListItem;
import com.agrilink.order.OrderDtos.OrderResponse;
import com.agrilink.order.OrderDtos.QuoteResponse;
import com.agrilink.order.OrderDtos.ReasonRequest;
import com.agrilink.order.OrderDtos.TimelineEntry;
import com.agrilink.security.AuthContext;
import com.agrilink.user.Role;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Transactional
public class OrderController {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");

    private final OrderService orders;
    private final OrderMapper mapper;

    public OrderController(OrderService orders, OrderMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    /** Price breakdown (goods, delivery, AgriLink fee) for a basket; nothing is reserved. */
    @PostMapping("/quote")
    @PreAuthorize("hasRole('BUYER')")
    @Transactional(readOnly = true)
    public QuoteResponse quote(@Valid @RequestBody CreateOrderRequest request) {
        return orders.quote(AuthContext.userId(), request);
    }

    /** Places an order with one farmer; stock is reserved until the farmer answers or the order expires. */
    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(orders.create(me, request), me, Role.BUYER);
    }

    /**
     * The caller's orders (as buyer, farmer or driver depending on role), newest first.
     * Filter with repeated {@code status} params and {@code from}/{@code to} dates (inclusive, Addis Ababa time).
     */
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<OrderListItem> list(
            @RequestParam(required = false) Set<OrderStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        UUID me = AuthContext.userId();
        Role role = AuthContext.role();
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZONE).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
        return PageResponse.of(orders.listFor(me, role, status, fromInstant, toInstant, pageable),
                o -> mapper.toListItem(o, me, role));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public OrderResponse get(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        Role role = AuthContext.role();
        return mapper.toResponse(orders.getForUser(id, me, role), me, role);
    }

    @GetMapping("/{id}/timeline")
    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(@PathVariable UUID id) {
        orders.getForUser(id, AuthContext.userId(), AuthContext.role());
        return orders.timeline(id).stream()
                .map(h -> new TimelineEntry(h.getFromStatus(), h.getToStatus(), h.getCreatedAt(),
                        h.getActorRole() == null ? "SYSTEM" : h.getActorRole().name(), h.getNote()))
                .toList();
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('FARMER')")
    public OrderResponse accept(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(orders.accept(me, id), me, Role.FARMER);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('FARMER')")
    public OrderResponse reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(orders.reject(me, id, request.reason()), me, Role.FARMER);
    }

    /** Farmer has packed the produce and it is waiting for the driver. */
    @PostMapping("/{id}/ready")
    @PreAuthorize("hasRole('FARMER')")
    public OrderResponse ready(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(orders.markReady(me, id), me, Role.FARMER);
    }

    /** Buyer accepts the delivery; releases the escrow to the farmer and driver. */
    @PostMapping("/{id}/confirm-delivery")
    @PreAuthorize("hasRole('BUYER')")
    public OrderResponse confirmDelivery(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return mapper.toResponse(orders.confirmDelivery(me, id), me, Role.BUYER);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('BUYER','FARMER')")
    public OrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        UUID me = AuthContext.userId();
        Role role = AuthContext.role();
        return mapper.toResponse(orders.cancel(me, role, id, request.reason()), me, role);
    }
}
