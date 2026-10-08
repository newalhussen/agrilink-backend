package com.agrilink.delivery;

import com.agrilink.common.Address;
import com.agrilink.common.ApiException;
import com.agrilink.common.ErrorCode;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.driver.DriverAvailability;
import com.agrilink.driver.DriverProfile;
import com.agrilink.driver.DriverProfileRepository;
import com.agrilink.file.FilePurpose;
import com.agrilink.file.FileService;
import com.agrilink.marketplace.Listing;
import com.agrilink.marketplace.ListingRepository;
import com.agrilink.order.Order;
import com.agrilink.order.OrderItem;
import com.agrilink.order.OrderService;
import com.agrilink.order.OrderStatus;
import com.agrilink.order.PricingService;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import com.agrilink.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver jobs: creation when an order is paid, job board, assignment, pickup and delivery handovers.
 * Locks are always taken order first, then delivery.
 */
@Service
public class DeliveryService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<DeliveryStatus> ACTIVE = EnumSet.of(DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP,
            DeliveryStatus.IN_TRANSIT);
    /** Weighed load differing from the order by more than this share is flagged for the buyer and ops. */
    private static final BigDecimal VARIANCE_THRESHOLD = new BigDecimal("0.05");

    private final DeliveryRepository deliveries;
    private final DeliveryEventRepository deliveryEvents;
    private final DeliveryAttachmentRepository attachments;
    private final OrderService orderService;
    private final ListingRepository listings;
    private final DriverProfileRepository driverProfiles;
    private final UserRepository users;
    private final FileService files;
    private final ApplicationEventPublisher events;
    private final AgriLinkProperties.Orders props;
    private final Clock clock;

    public DeliveryService(DeliveryRepository deliveries, DeliveryEventRepository deliveryEvents,
                           DeliveryAttachmentRepository attachments, OrderService orderService,
                           ListingRepository listings, DriverProfileRepository driverProfiles, UserRepository users,
                           FileService files, ApplicationEventPublisher events, AgriLinkProperties properties,
                           Clock clock) {
        this.deliveries = deliveries;
        this.deliveryEvents = deliveryEvents;
        this.attachments = attachments;
        this.orderService = orderService;
        this.listings = listings;
        this.driverProfiles = driverProfiles;
        this.users = users;
        this.files = files;
        this.events = events;
        this.props = properties.orders();
        this.clock = clock;
    }

    // ------------------------------------------------------------------ creation / cancellation (order events)

    /** Posts the job to the board when an order becomes PAID. Idempotent per order. */
    @Transactional
    public Delivery createForOrder(Order order) {
        return deliveries.findByOrderId(order.getId()).orElseGet(() -> {
            Address pickup = order.getItems().stream().findFirst()
                    .flatMap(i -> listings.findById(i.getListingId())).map(Listing::getAddress).map(Address::copy)
                    .orElseGet(Address::new);
            Delivery d = new Delivery(order, order.getTotalWeightKg(), order.getDeliveryFee(), code(), token(), code(),
                    token());
            d.setPickupAddress(pickup);
            d.setPickupContactName(order.getFarmer().getFullName());
            d.setPickupContactPhone(order.getFarmer().getPhone());
            d.setDropoffAddress(order.getDeliveryAddress().copy());
            d.setDropoffContactName(order.getDeliveryContactName());
            d.setDropoffContactPhone(order.getDeliveryContactPhone());
            d.setDistanceKm(com.agrilink.common.GeoUtils.distanceKm(pickup, order.getDeliveryAddress()));
            d.setScheduledPickupDate(order.getRequestedDeliveryDate());
            d = deliveries.save(d);
            event(d, DeliveryEvent.Type.CREATED, null, null, "Job posted", null);
            return d;
        });
    }

    /** Cancels the job when its order is cancelled or otherwise closed before pickup. */
    @Transactional
    public void cancelForOrder(UUID orderId, String reason) {
        deliveries.findByOrderId(orderId).ifPresent(d -> {
            if (d.getStatus() == DeliveryStatus.DELIVERED || d.getStatus() == DeliveryStatus.CANCELLED) {
                return;
            }
            UUID driverId = d.getDriver() == null ? null : d.getDriver().getId();
            d.setStatus(DeliveryStatus.CANCELLED);
            d.setCancelledReason(reason);
            event(d, DeliveryEvent.Type.CANCELLED, null, null, reason, null);
            publish(d);
            if (driverId != null) {
                refreshAvailability(driverId);
            }
        });
    }

    // ------------------------------------------------------------------ driver actions

    @Transactional(readOnly = true)
    public Page<Delivery> availableJobs(UUID driverId, UUID regionId, BigDecimal minFee, Pageable pageable) {
        User driver = requireDriver(driverId);
        DriverProfile profile = driverProfiles.findByUserId(driverId).orElseThrow();
        BigDecimal capacity = profile.getCapacityKg();
        var spec = DeliverySpecifications.open(driver.isVerified() ? capacity : BigDecimal.ZERO, regionId, minFee);
        return deliveries.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "scheduledPickupDate").and(Sort.by("createdAt")).and(Sort.by("id"))));
    }

    @Transactional(readOnly = true)
    public Page<Delivery> myDeliveries(UUID driverId, Set<DeliveryStatus> statuses, Pageable pageable) {
        return deliveries.findAll(DeliverySpecifications.forDriver(driverId, statuses),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
    }

    @Transactional
    public Delivery accept(UUID driverId, UUID deliveryId) {
        User driver = requireDriver(driverId);
        Delivery d = lockDelivery(deliveryId);
        if (d.getStatus() != DeliveryStatus.OPEN) {
            throw ApiException.conflict("This job is no longer available");
        }
        if (!driver.isVerified()) {
            throw new ApiException(ErrorCode.VERIFICATION_REQUIRED, "Your account must be verified to take jobs");
        }
        DriverProfile profile = driverProfiles.findByUserId(driverId)
                .orElseThrow(() -> ApiException.notFound("Driver profile"));
        if (!profile.hasVehicle()) {
            throw ApiException.badRequest("Add your vehicle details before taking jobs");
        }
        if (profile.getLicenseExpiryDate() != null
                && profile.getLicenseExpiryDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Africa/Addis_Ababa"))))) {
            throw ApiException.badRequest("Your driving licence has expired");
        }
        if (profile.getAvailability() == DriverAvailability.OFFLINE) {
            throw ApiException.badRequest("Go online before taking a job");
        }
        if (profile.getCapacityKg().compareTo(d.getTotalWeightKg()) < 0) {
            throw ApiException.badRequest("This load (" + d.getTotalWeightKg().setScale(0, RoundingMode.CEILING)
                    + " kg) is heavier than your vehicle capacity");
        }
        if (deliveries.countByDriverIdAndStatusIn(driverId, ACTIVE) >= props.maxActiveDeliveriesPerDriver()) {
            throw ApiException.badRequest("You already have the maximum number of active jobs");
        }
        Order order = d.getOrder();
        orderService.assignDriver(order, driver);
        d.setDriver(driver);
        d.setStatus(DeliveryStatus.ASSIGNED);
        d.setAssignedAt(Instant.now(clock));
        event(d, DeliveryEvent.Type.ASSIGNED, null, null, "Driver " + driver.getFullName() + " accepted", driverId);
        refreshAvailability(driverId);
        publish(d);
        return d;
    }

    /** Driver backs out before loading; the job returns to the board. */
    @Transactional
    public Delivery release(UUID driverId, UUID deliveryId) {
        Delivery d = lockDelivery(deliveryId);
        if (d.getDriver() == null || !d.getDriver().getId().equals(driverId)) {
            throw ApiException.notFound("Delivery");
        }
        if (d.getStatus() != DeliveryStatus.ASSIGNED) {
            throw ApiException.invalidTransition("Only jobs that are not yet picked up can be released");
        }
        orderService.clearDriver(d.getOrder());
        d.setDriver(null);
        d.setStatus(DeliveryStatus.OPEN);
        d.setAssignedAt(null);
        event(d, DeliveryEvent.Type.RELEASED, null, null, "Driver released the job", driverId);
        refreshAvailability(driverId);
        publish(d);
        return d;
    }

    /**
     * Pickup handover: the farmer shows the pickup code (or QR), the driver weighs and counts the load.
     * Failed codes are committed so the attempt counter holds.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Delivery pickup(UUID driverId, UUID deliveryId, String code, BigDecimal weighedKg, Integer crateCount,
                           String note, List<UUID> evidenceFileIds) {
        Delivery d = lockOwned(driverId, deliveryId);
        Order order = d.getOrder();
        if (d.getStatus() != DeliveryStatus.ASSIGNED) {
            throw ApiException.invalidTransition("This job is " + d.getStatus() + " and cannot be picked up");
        }
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw ApiException.invalidTransition("The order is " + order.getStatus() + " and cannot be collected");
        }
        d.verifyPickupCode(code, props.maxCodeAttempts());
        Instant now = Instant.now(clock);
        d.setStatus(DeliveryStatus.PICKED_UP);
        d.setPickedUpAt(now);
        d.setPickedUpWeightKg(weighedKg);
        d.setPickedUpCrateCount(crateCount);
        d.setPickupNote(note);
        saveEvidence(d, DeliveryAttachment.Stage.PICKUP, driverId, evidenceFileIds);
        event(d, DeliveryEvent.Type.PICKED_UP, null, null,
                "Picked up " + weighedKg.stripTrailingZeros().toPlainString() + " kg"
                        + (crateCount == null ? "" : ", " + crateCount + " crates"), driverId);
        BigDecimal ordered = d.getTotalWeightKg();
        if (ordered.signum() > 0 && weighedKg.subtract(ordered).abs()
                .divide(ordered, 4, RoundingMode.HALF_UP).compareTo(VARIANCE_THRESHOLD) > 0) {
            event(d, DeliveryEvent.Type.WEIGHT_VARIANCE, null, null, "Ordered " + ordered.stripTrailingZeros()
                    .toPlainString() + " kg, weighed " + weighedKg.stripTrailingZeros().toPlainString() + " kg", driverId);
        }
        orderService.transition(order, OrderStatus.PICKED_UP, driverId, Role.DRIVER, "Pickup code verified");
        publish(d);
        return d;
    }

    @Transactional
    public Delivery startTrip(UUID driverId, UUID deliveryId) {
        Delivery d = lockOwned(driverId, deliveryId);
        if (d.getStatus() != DeliveryStatus.PICKED_UP) {
            throw ApiException.invalidTransition("Pick up the load before starting the trip");
        }
        d.setStatus(DeliveryStatus.IN_TRANSIT);
        event(d, DeliveryEvent.Type.IN_TRANSIT, null, null, "On the road", driverId);
        orderService.transition(d.getOrder(), OrderStatus.IN_TRANSIT, driverId, Role.DRIVER, null);
        publish(d);
        return d;
    }

    @Transactional
    public void recordLocation(UUID driverId, UUID deliveryId, double lat, double lon, String note) {
        Delivery d = lockOwned(driverId, deliveryId);
        if (!d.getStatus().isActive()) {
            throw ApiException.invalidTransition("This delivery is not active");
        }
        driverProfiles.findByUserId(driverId).ifPresent(p -> p.updateLocation(lat, lon, Instant.now(clock)));
        event(d, DeliveryEvent.Type.LOCATION, lat, lon, note, driverId);
    }

    /** Delivery handover: the buyer shows the delivery code; this starts the buyer's check window. */
    @Transactional(noRollbackFor = ApiException.class)
    public Delivery deliver(UUID driverId, UUID deliveryId, String code, String note, List<UUID> evidenceFileIds) {
        Delivery d = lockOwned(driverId, deliveryId);
        if (d.getStatus() != DeliveryStatus.PICKED_UP && d.getStatus() != DeliveryStatus.IN_TRANSIT) {
            throw ApiException.invalidTransition("This job is " + d.getStatus() + " and cannot be delivered");
        }
        d.verifyDeliveryCode(code, props.maxCodeAttempts());
        d.setStatus(DeliveryStatus.DELIVERED);
        d.setDeliveredAt(Instant.now(clock));
        d.setDeliveryNote(note);
        saveEvidence(d, DeliveryAttachment.Stage.DELIVERY, driverId, evidenceFileIds);
        event(d, DeliveryEvent.Type.DELIVERED, null, null, "Delivered, code verified", driverId);
        orderService.markDelivered(d.getOrder(), driverId);
        refreshAvailability(driverId);
        publish(d);
        return d;
    }

    // ------------------------------------------------------------------ admin

    @Transactional
    public Delivery adminAssign(UUID adminId, UUID deliveryId, UUID driverId) {
        User driver = requireDriver(driverId);
        Delivery d = lockDelivery(deliveryId);
        if (d.getStatus() != DeliveryStatus.OPEN && d.getStatus() != DeliveryStatus.ASSIGNED) {
            throw ApiException.invalidTransition("Only open or assigned jobs can be (re)assigned");
        }
        if (d.getDriver() != null) {
            UUID previous = d.getDriver().getId();
            orderService.clearDriver(d.getOrder());
            d.setDriver(null);
            refreshAvailability(previous);
        }
        orderService.assignDriver(d.getOrder(), driver);
        d.setDriver(driver);
        d.setStatus(DeliveryStatus.ASSIGNED);
        d.setAssignedAt(Instant.now(clock));
        event(d, DeliveryEvent.Type.ASSIGNED, null, null, "Assigned by operations to " + driver.getFullName(), adminId);
        refreshAvailability(driverId);
        publish(d);
        return d;
    }

    @Transactional
    public Delivery adminUnassign(UUID adminId, UUID deliveryId) {
        Delivery d = lockDelivery(deliveryId);
        if (d.getStatus() != DeliveryStatus.ASSIGNED || d.getDriver() == null) {
            throw ApiException.invalidTransition("Only assigned jobs that have not been picked up can be unassigned");
        }
        UUID previous = d.getDriver().getId();
        orderService.clearDriver(d.getOrder());
        d.setDriver(null);
        d.setStatus(DeliveryStatus.OPEN);
        d.setAssignedAt(null);
        event(d, DeliveryEvent.Type.RELEASED, null, null, "Unassigned by operations", adminId);
        refreshAvailability(previous);
        publish(d);
        return d;
    }

    @Transactional
    public Delivery adminResetCodeAttempts(UUID deliveryId) {
        Delivery d = lockDelivery(deliveryId);
        d.resetCodeAttempts();
        return d;
    }

    @Transactional(readOnly = true)
    public Page<Delivery> adminSearch(Set<DeliveryStatus> statuses, UUID driverId, String q, Pageable pageable) {
        return deliveries.findAll(DeliverySpecifications.admin(statuses, driverId, q),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Delivery getForUser(UUID deliveryId, UUID userId, Role role) {
        Delivery d = deliveries.findWithDetailsById(deliveryId).orElseThrow(() -> ApiException.notFound("Delivery"));
        assertVisible(d, userId, role);
        return d;
    }

    @Transactional(readOnly = true)
    public Delivery getForOrder(UUID orderId, UUID userId, Role role) {
        Delivery d = deliveries.findByOrderId(orderId).orElseThrow(() -> ApiException.notFound("Delivery"));
        assertVisible(d, userId, role);
        return d;
    }

    @Transactional(readOnly = true)
    public List<DeliveryEvent> timeline(UUID deliveryId) {
        return deliveryEvents.findByDeliveryIdOrderByCreatedAtAsc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttachment> attachments(UUID deliveryId) {
        return attachments.findByDeliveryIdOrderByCreatedAtAsc(deliveryId);
    }

    /** Called when an order completes or is cancelled: frees driver capacity and counts the finished trip. */
    @Transactional
    public void onOrderCompleted(UUID orderId) {
        deliveries.findByOrderId(orderId).ifPresent(d -> {
            if (d.getDriver() != null) {
                driverProfiles.findByUserId(d.getDriver().getId()).ifPresent(DriverProfile::incrementCompletedDeliveries);
                refreshAvailability(d.getDriver().getId());
            }
        });
    }

    // ------------------------------------------------------------------ internals

    private void assertVisible(Delivery d, UUID userId, Role role) {
        if (role == Role.ADMIN) {
            return;
        }
        Order o = d.getOrder();
        boolean driver = d.getDriver() != null && d.getDriver().getId().equals(userId);
        boolean party = o.getBuyer().getId().equals(userId) || o.getFarmer().getId().equals(userId);
        // Drivers may look at a job on the board before accepting it (the mapper hides exact addresses until then).
        boolean openJob = role == Role.DRIVER && d.getStatus() == DeliveryStatus.OPEN;
        if (!driver && !party && !openJob) {
            throw ApiException.notFound("Delivery");
        }
    }

    private Delivery lockDelivery(UUID deliveryId) {
        UUID orderId = deliveries.findOrderIdById(deliveryId).orElseThrow(() -> ApiException.notFound("Delivery"));
        orderService.lock(orderId);
        return deliveries.lockById(deliveryId).orElseThrow(() -> ApiException.notFound("Delivery"));
    }

    private Delivery lockOwned(UUID driverId, UUID deliveryId) {
        Delivery d = lockDelivery(deliveryId);
        if (d.getDriver() == null || !d.getDriver().getId().equals(driverId)) {
            throw ApiException.notFound("Delivery");
        }
        return d;
    }

    private User requireDriver(UUID driverId) {
        User user = users.findById(driverId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getRole() != Role.DRIVER) {
            throw ApiException.forbidden("Only drivers can do this");
        }
        return user;
    }

    private void saveEvidence(Delivery d, DeliveryAttachment.Stage stage, UUID userId, List<UUID> fileIds) {
        if (fileIds == null) {
            return;
        }
        Instant now = Instant.now(clock);
        for (UUID fileId : fileIds) {
            files.requireOwned(fileId, userId, FilePurpose.DELIVERY_EVIDENCE);
            attachments.save(new DeliveryAttachment(d, stage, fileId, userId, now));
        }
    }

    private void event(Delivery d, DeliveryEvent.Type type, Double lat, Double lon, String note, UUID actorId) {
        deliveryEvents.save(new DeliveryEvent(d.getId(), type, lat, lon, note, actorId, Instant.now(clock)));
    }

    private void publish(Delivery d) {
        Order o = d.getOrder();
        events.publishEvent(new DeliveryStatusChangedEvent(d.getId(), o.getId(), o.getOrderNumber(),
                o.getBuyer().getId(), o.getFarmer().getId(), d.getDriver() == null ? null : d.getDriver().getId(),
                d.getDriver() == null ? null : d.getDriver().getFullName(), d.getStatus(),
                d.getPickedUpWeightKg() != null ? d.getPickedUpWeightKg() : d.getTotalWeightKg()));
    }

    /** BUSY while at the active-job limit, AVAILABLE again once below it (never overrides OFFLINE). */
    private void refreshAvailability(UUID driverId) {
        driverProfiles.findByUserId(driverId).ifPresent(p -> {
            long active = deliveries.countByDriverIdAndStatusIn(driverId, ACTIVE);
            if (p.getAvailability() == DriverAvailability.AVAILABLE && active >= props.maxActiveDeliveriesPerDriver()) {
                p.setAvailability(DriverAvailability.BUSY);
            } else if (p.getAvailability() == DriverAvailability.BUSY && active < props.maxActiveDeliveriesPerDriver()) {
                p.setAvailability(DriverAvailability.AVAILABLE);
            }
        });
    }

    private static String code() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String token() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
