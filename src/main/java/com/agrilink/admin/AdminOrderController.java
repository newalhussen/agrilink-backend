package com.agrilink.admin;

import com.agrilink.common.PageResponse;
import com.agrilink.delivery.Delivery;
import com.agrilink.delivery.DeliveryDtos.AssignDriverRequest;
import com.agrilink.delivery.DeliveryDtos.DeliveryEventResponse;
import com.agrilink.delivery.DeliveryDtos.DeliveryResponse;
import com.agrilink.delivery.DeliveryMapper;
import com.agrilink.delivery.DeliveryService;
import com.agrilink.delivery.DeliveryStatus;
import com.agrilink.dispute.DisputeDtos.DisputeResponse;
import com.agrilink.dispute.DisputeMapper;
import com.agrilink.dispute.DisputeService;
import com.agrilink.driver.DriverAvailability;
import com.agrilink.driver.DriverProfile;
import com.agrilink.driver.DriverProfileRepository;
import com.agrilink.order.Order;
import com.agrilink.order.OrderDtos.OrderListItem;
import com.agrilink.order.OrderDtos.OrderResponse;
import com.agrilink.order.OrderDtos.ReasonRequest;
import com.agrilink.order.OrderDtos.TimelineEntry;
import com.agrilink.order.OrderMapper;
import com.agrilink.order.OrderService;
import com.agrilink.order.OrderStatus;
import com.agrilink.payment.PaymentDtos.AdminPaymentResponse;
import com.agrilink.payment.PaymentService;
import com.agrilink.security.AuthContext;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Operations: orders and deliveries (live board, assignment, intervention). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminOrderController {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final Set<DeliveryStatus> ACTIVE = EnumSet.of(DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP,
            DeliveryStatus.IN_TRANSIT);

    private final OrderService orders;
    private final OrderMapper orderMapper;
    private final PaymentService payments;
    private final DeliveryService deliveries;
    private final DeliveryMapper deliveryMapper;
    private final DisputeService disputes;
    private final DisputeMapper disputeMapper;
    private final UserRepository users;
    private final DriverProfileRepository driverProfiles;
    private final com.agrilink.delivery.DeliveryRepository deliveryRepository;
    private final AdminAuditService audit;

    public AdminOrderController(OrderService orders, OrderMapper orderMapper, PaymentService payments,
                                DeliveryService deliveries, DeliveryMapper deliveryMapper, DisputeService disputes,
                                DisputeMapper disputeMapper, UserRepository users,
                                DriverProfileRepository driverProfiles,
                                com.agrilink.delivery.DeliveryRepository deliveryRepository,
                                AdminAuditService audit) {
        this.orders = orders;
        this.orderMapper = orderMapper;
        this.payments = payments;
        this.deliveries = deliveries;
        this.deliveryMapper = deliveryMapper;
        this.disputes = disputes;
        this.disputeMapper = disputeMapper;
        this.users = users;
        this.driverProfiles = driverProfiles;
        this.deliveryRepository = deliveryRepository;
        this.audit = audit;
    }

    public record OrderDetail(OrderResponse order, List<TimelineEntry> timeline, List<AdminPaymentResponse> payments,
                              List<DeliveryEventResponse> deliveryEvents, List<DisputeResponse> disputes) {}

    public record DriverOption(UUID id, String fullName, String phone, String vehicleType, String vehiclePlate,
                               BigDecimal capacityKg, DriverAvailability availability, long activeJobs,
                               boolean verified) {}

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public PageResponse<OrderListItem> orders(
            @RequestParam(required = false) Set<OrderStatus> status,
            @RequestParam(required = false) UUID buyerId, @RequestParam(required = false) UUID farmerId,
            @RequestParam(required = false) UUID driverId, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        Instant f = from == null ? null : from.atStartOfDay(ZONE).toInstant();
        Instant t = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
        UUID me = AuthContext.userId();
        return PageResponse.of(orders.adminSearch(status, f, t, buyerId, farmerId, driverId, q, pageable),
                o -> orderMapper.toListItem(o, me, Role.ADMIN));
    }

    @GetMapping("/orders/{id}")
    @Transactional(readOnly = true)
    public OrderDetail order(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        Order order = orders.getForUser(id, me, Role.ADMIN);
        List<DeliveryEventResponse> events = deliveryRepository.findByOrderId(id)
                .map(d -> deliveries.timeline(d.getId()).stream().map(deliveryMapper::toEvent).toList())
                .orElse(List.of());
        return new OrderDetail(orderMapper.toResponse(order, me, Role.ADMIN),
                orders.timeline(id).stream().map(h -> new TimelineEntry(h.getFromStatus(), h.getToStatus(),
                        h.getCreatedAt(), h.getActorRole() == null ? "SYSTEM" : h.getActorRole().name(), h.getNote()))
                        .toList(),
                payments.forOrder(id).stream().map(AdminPaymentResponse::from).toList(), events,
                disputes.forOrder(id, me, Role.ADMIN).stream().map(d -> disputeMapper.toResponse(d, true)).toList());
    }

    /** Cancels an order that has not been picked up and refunds any held money. */
    @PostMapping("/orders/{id}/cancel")
    @Transactional
    public OrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        UUID me = AuthContext.userId();
        Order order = orders.cancel(me, Role.ADMIN, id, request.reason());
        audit.record(me, "ORDER_CANCELLED", "ORDER", id, request.reason());
        return orderMapper.toResponse(order, me, Role.ADMIN);
    }

    @GetMapping("/deliveries")
    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> deliveries(@RequestParam(required = false) Set<DeliveryStatus> status,
                                                     @RequestParam(required = false) UUID driverId,
                                                     @RequestParam(required = false) String q, Pageable pageable) {
        UUID me = AuthContext.userId();
        return PageResponse.of(deliveries.adminSearch(status, driverId, q, pageable),
                d -> deliveryMapper.toResponse(d, me, Role.ADMIN));
    }

    @GetMapping("/deliveries/{id}")
    @Transactional(readOnly = true)
    public DeliveryResponse delivery(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        return deliveryMapper.toResponse(deliveries.getForUser(id, me, Role.ADMIN), me, Role.ADMIN);
    }

    @GetMapping("/deliveries/{id}/events")
    @Transactional(readOnly = true)
    public List<DeliveryEventResponse> deliveryEvents(@PathVariable UUID id) {
        return deliveries.timeline(id).stream().map(deliveryMapper::toEvent).toList();
    }

    @PostMapping("/deliveries/{id}/assign")
    @Transactional
    public DeliveryResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignDriverRequest request) {
        UUID me = AuthContext.userId();
        Delivery d = deliveries.adminAssign(me, id, request.driverId());
        audit.record(me, "DELIVERY_ASSIGNED", "DELIVERY", id, "driver=" + request.driverId());
        return deliveryMapper.toResponse(d, me, Role.ADMIN);
    }

    @PostMapping("/deliveries/{id}/unassign")
    @Transactional
    public DeliveryResponse unassign(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        Delivery d = deliveries.adminUnassign(me, id);
        audit.record(me, "DELIVERY_UNASSIGNED", "DELIVERY", id, null);
        return deliveryMapper.toResponse(d, me, Role.ADMIN);
    }

    /** Unlocks a pickup or delivery that was locked after too many wrong codes. */
    @PostMapping("/deliveries/{id}/reset-codes")
    @Transactional
    public DeliveryResponse resetCodes(@PathVariable UUID id) {
        UUID me = AuthContext.userId();
        Delivery d = deliveries.adminResetCodeAttempts(id);
        audit.record(me, "DELIVERY_CODES_RESET", "DELIVERY", id, null);
        return deliveryMapper.toResponse(d, me, Role.ADMIN);
    }

    /** Verified drivers that could take a job (for the assign dialog). */
    @GetMapping("/drivers/available")
    @Transactional(readOnly = true)
    public List<DriverOption> availableDrivers() {
        List<DriverOption> out = new java.util.ArrayList<>();
        for (User u : users.findAll()) {
            if (u.getRole() != Role.DRIVER || !u.isActive() || !u.isVerified()) {
                continue;
            }
            DriverProfile p = driverProfiles.findByUserId(u.getId()).orElse(null);
            if (p == null || !p.hasVehicle() || p.getAvailability() == DriverAvailability.OFFLINE) {
                continue;
            }
            out.add(new DriverOption(u.getId(), u.getFullName(), u.getPhone(), p.getVehicleType().name(),
                    p.getVehiclePlate(), p.getCapacityKg(), p.getAvailability(),
                    deliveryRepository.countByDriverIdAndStatusIn(u.getId(), ACTIVE), u.isVerified()));
        }
        return out;
    }
}
