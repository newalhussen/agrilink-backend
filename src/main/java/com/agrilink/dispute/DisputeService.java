package com.agrilink.dispute;

import com.agrilink.admin.AdminAuditService;
import com.agrilink.common.Money;
import com.agrilink.common.ApiException;
import com.agrilink.config.AgriLinkProperties;
import com.agrilink.delivery.DeliveryRepository;
import com.agrilink.dispute.DisputeEnums.DisputeStatus;
import com.agrilink.dispute.DisputeEnums.DisputeType;
import com.agrilink.dispute.DisputeEnums.EvidenceKind;
import com.agrilink.dispute.DisputeEnums.ResolutionType;
import com.agrilink.file.FilePurpose;
import com.agrilink.file.FileService;
import com.agrilink.order.Order;
import com.agrilink.order.OrderService;
import com.agrilink.order.OrderStatus;
import com.agrilink.payment.PaymentService;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * Problem reports on orders. Opening one freezes the order (DISPUTED) so no money moves; an admin then settles
 * the held payment explicitly and the order closes as COMPLETED or CANCELLED.
 */
@Service
public class DisputeService {

    private final DisputeRepository disputes;
    private final DisputeEvidenceRepository evidence;
    private final OrderService orderService;
    private final PaymentService payments;
    private final DeliveryRepository deliveries;
    private final FileService files;
    private final AdminAuditService audit;
    private final ApplicationEventPublisher events;
    private final AgriLinkProperties.Disputes props;
    private final Clock clock;

    public DisputeService(DisputeRepository disputes, DisputeEvidenceRepository evidence, OrderService orderService,
                          PaymentService payments, DeliveryRepository deliveries, FileService files,
                          AdminAuditService audit, ApplicationEventPublisher events, AgriLinkProperties properties,
                          Clock clock) {
        this.disputes = disputes;
        this.evidence = evidence;
        this.orderService = orderService;
        this.payments = payments;
        this.deliveries = deliveries;
        this.files = files;
        this.audit = audit;
        this.events = events;
        this.props = properties.disputes();
        this.clock = clock;
    }

    @Transactional
    public Dispute open(UUID userId, Role role, UUID orderId, DisputeType type, String description,
                        BigDecimal claimedReceivedQuantity, List<UUID> evidenceFileIds) {
        Order order = orderService.lock(orderId);
        if (!order.isParticipant(userId)) {
            throw ApiException.notFound("Order");
        }
        if (!OrderStatus.DISPUTABLE.contains(order.getStatus())) {
            throw ApiException.invalidTransition(
                    "A problem can be reported while the order is paid, on its way or just delivered."
                            + " This order is " + order.getStatus());
        }
        User raiser = role == Role.BUYER ? order.getBuyer() : role == Role.FARMER ? order.getFarmer() : order.getDriver();
        User against = againstFor(order, role, type);
        UUID deliveryId = deliveries.findByOrderId(orderId).map(d -> d.getId()).orElse(null);
        Instant now = Instant.now(clock);
        Dispute dispute = disputes.save(new Dispute(String.format("DSP-%04d", disputes.nextNumber()), order,
                deliveryId, raiser, against, type, description, claimedReceivedQuantity,
                now.plus(props.resolutionTarget())));
        addEvidenceInternal(dispute, userId, evidenceFileIds, description);
        orderService.markDisputed(order, userId, role,
                "Problem reported (" + type.name().toLowerCase().replace('_', ' ') + "): " + description);
        publish(DisputeChangedEvent.Kind.OPENED, dispute, userId);
        return dispute;
    }

    @Transactional
    public DisputeEvidence addEvidence(UUID userId, Role role, UUID disputeId, EvidenceKind kind, UUID fileId,
                                       String note) {
        Dispute d = disputes.findWithDetailsById(disputeId).orElseThrow(() -> ApiException.notFound("Dispute"));
        assertParticipantOrAdmin(d, userId, role);
        if (!d.getStatus().isOpen()) {
            throw ApiException.conflict("This dispute is already resolved");
        }
        if (fileId == null && (note == null || note.isBlank())) {
            throw ApiException.badRequest("Attach a photo or write a note");
        }
        if (fileId != null) {
            files.requireOwned(fileId, userId, FilePurpose.DISPUTE_EVIDENCE);
        }
        DisputeEvidence e = evidence.save(new DisputeEvidence(d, userId, kind, fileId, note));
        publish(DisputeChangedEvent.Kind.EVIDENCE_ADDED, d, userId);
        return e;
    }

    @Transactional
    public Dispute assign(UUID adminId, UUID disputeId) {
        Dispute d = disputes.lockById(disputeId).orElseThrow(() -> ApiException.notFound("Dispute"));
        if (!d.getStatus().isOpen()) {
            throw ApiException.conflict("This dispute is already resolved");
        }
        d.assignTo(adminId);
        audit.record(adminId, "DISPUTE_ASSIGN", "DISPUTE", d.getId(), d.getDisputeNumber());
        return d;
    }

    /**
     * Settles the held payment and closes the order. For PARTIAL the amounts must fit within the held total;
     * the remainder stays with AgriLink.
     */
    @Transactional
    public Dispute resolve(UUID adminId, UUID disputeId, ResolutionType type, BigDecimal farmerAmount,
                           BigDecimal driverAmount, BigDecimal buyerRefundAmount, String notes) {
        Dispute d = disputes.lockById(disputeId).orElseThrow(() -> ApiException.notFound("Dispute"));
        if (!d.getStatus().isOpen()) {
            throw ApiException.conflict("This dispute is already resolved");
        }
        Order order = orderService.lock(d.getOrder().getId());
        if (order.getStatus() != OrderStatus.DISPUTED) {
            throw ApiException.invalidTransition("The order is " + order.getStatus() + ", not DISPUTED");
        }
        BigDecimal farmer;
        BigDecimal driver;
        BigDecimal refund;
        switch (type) {
            case RELEASE_ALL -> {
                farmer = order.getSubtotalAmount();
                driver = order.getDriver() == null ? Money.ZERO : order.getDeliveryFee();
                refund = Money.ZERO;
            }
            case FULL_REFUND -> {
                farmer = Money.ZERO;
                driver = Money.ZERO;
                refund = null;
            }
            case PARTIAL -> {
                farmer = Money.orZero(farmerAmount);
                driver = Money.orZero(driverAmount);
                refund = Money.orZero(buyerRefundAmount);
            }
            default -> throw ApiException.badRequest("Unknown resolution");
        }
        com.agrilink.payment.Payment payment;
        if (type == ResolutionType.FULL_REFUND) {
            payment = payments.refundHeld(order, "Dispute " + d.getDisputeNumber() + ": " + notes);
            refund = payment.getRefundedAmount();
        } else {
            payment = payments.settle(order, farmer, driver, refund,
                    "Dispute " + d.getDisputeNumber() + ": " + notes);
        }
        BigDecimal platform = payment.getPlatformFeeRetained();
        boolean fullyRefunded = refund.compareTo(payment.getAmount()) >= 0;
        d.resolve(type, farmer, driver, refund, platform, notes, adminId, Instant.now(clock));
        orderService.finishDispute(order, !fullyRefunded, adminId, "Dispute " + d.getDisputeNumber() + " resolved: "
                + type);
        audit.record(adminId, "DISPUTE_RESOLVE", "DISPUTE", d.getId(),
                type + " farmer=" + farmer + " driver=" + driver + " refund=" + refund + " | " + notes);
        publish(DisputeChangedEvent.Kind.RESOLVED, d, adminId);
        return d;
    }

    @Transactional(readOnly = true)
    public Dispute getForUser(UUID disputeId, UUID userId, Role role) {
        Dispute d = disputes.findWithDetailsById(disputeId).orElseThrow(() -> ApiException.notFound("Dispute"));
        assertParticipantOrAdmin(d, userId, role);
        return d;
    }

    @Transactional(readOnly = true)
    public List<DisputeEvidence> evidenceFor(UUID disputeId) {
        return evidence.findByDisputeIdOrderByCreatedAtAsc(disputeId);
    }

    @Transactional(readOnly = true)
    public List<Dispute> forOrder(UUID orderId, UUID userId, Role role) {
        List<Dispute> list = disputes.findByOrderId(orderId);
        if (role != Role.ADMIN) {
            list = list.stream().filter(d -> d.getOrder().isParticipant(userId)).toList();
        }
        return list;
    }

    @Transactional(readOnly = true)
    public Page<Dispute> mine(UUID userId, Set<DisputeStatus> statuses, Pageable pageable) {
        return disputes.findAll(DisputeSpecifications.forParticipant(userId, statuses), newest(pageable));
    }

    @Transactional(readOnly = true)
    public Page<Dispute> adminSearch(Set<DisputeStatus> statuses, Boolean overdueOnly, String q, Pageable pageable) {
        return disputes.findAll(DisputeSpecifications.admin(statuses, Boolean.TRUE.equals(overdueOnly)
                ? Instant.now(clock) : null, q), PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "dueAt").and(Sort.by("id"))));
    }

    private Pageable newest(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    private void addEvidenceInternal(Dispute d, UUID userId, List<UUID> fileIds, String note) {
        if (fileIds != null) {
            for (UUID fileId : fileIds) {
                files.requireOwned(fileId, userId, FilePurpose.DISPUTE_EVIDENCE);
                evidence.save(new DisputeEvidence(d, userId, EvidenceKind.PHOTO, fileId, null));
            }
        }
        if (note != null && !note.isBlank()) {
            evidence.save(new DisputeEvidence(d, userId, EvidenceKind.NOTE, null, note));
        }
    }

    private void assertParticipantOrAdmin(Dispute d, UUID userId, Role role) {
        if (role != Role.ADMIN && !d.getOrder().isParticipant(userId)) {
            throw ApiException.notFound("Dispute");
        }
    }

    /** Heuristic for who is being complained about; admins decide the real outcome. */
    private User againstFor(Order order, Role raiserRole, DisputeType type) {
        if (raiserRole == Role.BUYER) {
            boolean transport = type == DisputeType.DAMAGED || type == DisputeType.NOT_DELIVERED;
            return transport && order.getDriver() != null ? order.getDriver() : order.getFarmer();
        }
        if (raiserRole == Role.FARMER) {
            return order.getBuyer();
        }
        return order.getBuyer();
    }

    private void publish(DisputeChangedEvent.Kind kind, Dispute d, UUID actorId) {
        List<UUID> parties = new ArrayList<>();
        parties.add(d.getOrder().getBuyer().getId());
        parties.add(d.getOrder().getFarmer().getId());
        if (d.getOrder().getDriver() != null) {
            parties.add(d.getOrder().getDriver().getId());
        }
        events.publishEvent(new DisputeChangedEvent(kind, d.getId(), d.getDisputeNumber(), d.getOrder().getId(),
                d.getOrder().getOrderNumber(), actorId, parties));
    }

    public static Set<DisputeStatus> openStatuses() {
        return EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW);
    }
}
