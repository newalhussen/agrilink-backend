package com.agrilink.notification;

import com.agrilink.delivery.DeliveryStatusChangedEvent;
import com.agrilink.dispute.DisputeChangedEvent;
import com.agrilink.order.Order;
import com.agrilink.order.OrderItem;
import com.agrilink.order.OrderRepository;
import com.agrilink.order.OrderStatusChangedEvent;
import com.agrilink.payment.EscrowReleasedEvent;
import com.agrilink.payment.PaymentRepository;
import com.agrilink.payment.PaymentStatus;
import com.agrilink.payment.PaymentStatusChangedEvent;
import com.agrilink.rating.RatingCreatedEvent;
import com.agrilink.verification.VerificationDecidedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns domain events into notifications. Orders, payments, deliveries and disputes know nothing about
 * notifications; to change who is told what, edit this class (and the message bundles).
 */
@Component
public class NotificationEventListener {

    private static final ZoneId ZONE = ZoneId.of("Africa/Addis_Ababa");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(ZONE);

    private final NotificationService notifications;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public NotificationEventListener(NotificationService notifications, OrderRepository orders,
                                     PaymentRepository payments) {
        this.notifications = notifications;
        this.orders = orders;
        this.payments = payments;
    }

    @EventListener
    public void on(OrderStatusChangedEvent e) {
        UUID id = e.orderId();
        String no = e.orderNumber();
        switch (e.to()) {
            case PENDING -> orders.findWithDetailsById(id).ifPresent(o -> notifications.send(e.farmerId(),
                    NotificationType.ORDER_CREATED, "ORDER", id, no, o.getBuyer().getFullName(), summary(o),
                    money(o.getTotalAmount()), "2 hours"));
            case ACCEPTED -> notifications.send(e.buyerId(), NotificationType.ORDER_ACCEPTED, "ORDER", id, no);
            case REJECTED -> notifications.send(e.buyerId(), NotificationType.ORDER_REJECTED, "ORDER", id, no,
                    reason(e.reason()));
            case CANCELLED -> {
                for (UUID party : new UUID[]{e.buyerId(), e.farmerId(), e.driverId()}) {
                    if (party != null && !Objects.equals(party, e.actorId())) {
                        notifications.send(party, NotificationType.ORDER_CANCELLED, "ORDER", id, no, reason(e.reason()));
                    }
                }
            }
            case EXPIRED -> {
                notifications.send(e.buyerId(), NotificationType.ORDER_EXPIRED, "ORDER", id, no);
                notifications.send(e.farmerId(), NotificationType.ORDER_EXPIRED, "ORDER", id, no);
            }
            case READY_FOR_PICKUP -> {
                notifications.send(e.buyerId(), NotificationType.ORDER_READY_FOR_PICKUP, "ORDER", id, no);
                notifications.send(e.driverId(), NotificationType.ORDER_READY_FOR_PICKUP, "ORDER", id, no);
            }
            case COMPLETED -> {
                notifications.send(e.buyerId(), NotificationType.ORDER_COMPLETED, "ORDER", id, no);
                notifications.send(e.farmerId(), NotificationType.ORDER_COMPLETED, "ORDER", id, no);
                notifications.send(e.driverId(), NotificationType.ORDER_COMPLETED, "ORDER", id, no);
            }
            default -> { }
        }
    }

    @EventListener
    public void on(PaymentStatusChangedEvent e) {
        UUID id = e.orderId();
        switch (e.to()) {
            case HELD -> {
                notifications.send(e.buyerId(), NotificationType.PAYMENT_HELD, "ORDER", id, e.orderNumber(),
                        money(e.amount()));
                notifications.send(e.farmerId(), NotificationType.PAYMENT_HELD, "ORDER", id, e.orderNumber(),
                        money(e.amount()));
            }
            case FAILED -> orders.findById(id).ifPresent(o -> notifications.send(e.buyerId(),
                    NotificationType.PAYMENT_FAILED, "ORDER", id, e.orderNumber(),
                    o.getPaymentDeadline() == null ? "-" : time(o.getPaymentDeadline())));
            case REFUNDED, PARTIALLY_REFUNDED -> {
                if (e.from() == PaymentStatus.HELD || e.from() == PaymentStatus.CANCELLED
                        || e.from() == PaymentStatus.EXPIRED || e.from() == PaymentStatus.FAILED) {
                    payments.findById(e.paymentId()).ifPresent(p -> {
                        if (p.getRefundedAmount().signum() > 0) {
                            notifications.send(e.buyerId(), NotificationType.PAYMENT_REFUNDED, "ORDER", id,
                                    e.orderNumber(), money(p.getRefundedAmount()));
                        }
                    });
                }
            }
            default -> { }
        }
    }

    @EventListener
    public void on(EscrowReleasedEvent e) {
        notifications.send(e.farmerId(), NotificationType.PAYOUT_RELEASED, "ORDER", e.orderId(), e.orderNumber(),
                money(e.farmerAmount()));
        if (e.driverId() != null && e.driverAmount().signum() > 0) {
            notifications.send(e.driverId(), NotificationType.PAYOUT_RELEASED, "ORDER", e.orderId(), e.orderNumber(),
                    money(e.driverAmount()));
        }
    }

    @EventListener
    public void on(DeliveryStatusChangedEvent e) {
        UUID id = e.orderId();
        switch (e.status()) {
            case ASSIGNED -> {
                notifications.send(e.buyerId(), NotificationType.DELIVERY_ASSIGNED, "ORDER", id, e.orderNumber(),
                        e.driverName());
                notifications.send(e.farmerId(), NotificationType.DELIVERY_ASSIGNED, "ORDER", id, e.orderNumber(),
                        e.driverName());
            }
            case PICKED_UP -> notifications.send(e.buyerId(), NotificationType.DELIVERY_PICKED_UP, "ORDER", id,
                    e.orderNumber(), e.weightKg().stripTrailingZeros().toPlainString());
            case IN_TRANSIT -> notifications.send(e.buyerId(), NotificationType.DELIVERY_IN_TRANSIT, "ORDER", id,
                    e.orderNumber());
            case DELIVERED -> orders.findById(id).ifPresent(o -> {
                String until = o.getCheckWindowEndsAt() == null ? "-" : time(o.getCheckWindowEndsAt());
                notifications.send(e.buyerId(), NotificationType.DELIVERY_DELIVERED, "ORDER", id, e.orderNumber(), until);
                notifications.send(e.farmerId(), NotificationType.DELIVERY_DELIVERED, "ORDER", id, e.orderNumber(), until);
            });
            default -> { }
        }
    }

    @EventListener
    public void on(DisputeChangedEvent e) {
        NotificationType type = switch (e.kind()) {
            case OPENED -> NotificationType.DISPUTE_OPENED;
            case EVIDENCE_ADDED -> NotificationType.DISPUTE_UPDATED;
            case RESOLVED -> NotificationType.DISPUTE_RESOLVED;
        };
        for (UUID party : e.parties()) {
            boolean skipActor = e.kind() != DisputeChangedEvent.Kind.RESOLVED && Objects.equals(party, e.actorId());
            if (!skipActor) {
                notifications.send(party, type, "DISPUTE", e.disputeId(), e.orderNumber(), e.disputeNumber());
            }
        }
    }

    @EventListener
    public void on(VerificationDecidedEvent e) {
        NotificationType type = switch (e.decision()) {
            case APPROVE -> NotificationType.VERIFICATION_APPROVED;
            case REJECT -> NotificationType.VERIFICATION_REJECTED;
            case REQUEST_INFO -> NotificationType.VERIFICATION_INFO_NEEDED;
        };
        notifications.send(e.userId(), type, "VERIFICATION", e.userId(), reason(e.note()));
    }

    @EventListener
    public void on(RatingCreatedEvent e) {
        notifications.send(e.rateeId(), NotificationType.RATING_RECEIVED, null, null, e.score(), e.orderNumber());
    }

    private static String summary(Order o) {
        return o.getItems().stream().map(OrderItem::getProductName).distinct().limit(3)
                .collect(Collectors.joining(", ")) + " (" + o.getTotalWeightKg().setScale(0, java.math.RoundingMode.CEILING)
                .toPlainString() + " kg)";
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "%,.2f", amount);
    }

    private static String time(Instant instant) {
        return TIME.format(instant);
    }

    private static String reason(String reason) {
        return reason == null || reason.isBlank() ? "-" : reason;
    }
}
