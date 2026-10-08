package com.agrilink.order;

import com.agrilink.buyer.BuyerProfile;
import com.agrilink.buyer.BuyerProfileRepository;
import com.agrilink.common.AddressDto;
import com.agrilink.delivery.Delivery;
import com.agrilink.delivery.DeliveryRepository;
import com.agrilink.farmer.FarmerProfile;
import com.agrilink.farmer.FarmerProfileRepository;
import com.agrilink.order.OrderDtos.Amounts;
import com.agrilink.order.OrderDtos.Deadlines;
import com.agrilink.order.OrderDtos.DeliverySummary;
import com.agrilink.order.OrderDtos.OrderItemResponse;
import com.agrilink.order.OrderDtos.OrderListItem;
import com.agrilink.order.OrderDtos.OrderResponse;
import com.agrilink.order.OrderDtos.PartyView;
import com.agrilink.order.OrderDtos.PaymentSummary;
import com.agrilink.order.OrderDtos.Timestamps;
import com.agrilink.payment.Payment;
import com.agrilink.payment.PaymentRepository;
import com.agrilink.rating.RatingRepository;
import com.agrilink.region.RegionCatalog;
import com.agrilink.user.Role;
import com.agrilink.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds order views for one viewer: contact details, handover codes and available actions are role-scoped. */
@Component
public class OrderMapper {

    private final DeliveryRepository deliveries;
    private final PaymentRepository payments;
    private final FarmerProfileRepository farmerProfiles;
    private final BuyerProfileRepository buyerProfiles;
    private final RatingRepository ratings;
    private final RegionCatalog regions;

    public OrderMapper(DeliveryRepository deliveries, PaymentRepository payments,
                       FarmerProfileRepository farmerProfiles, BuyerProfileRepository buyerProfiles,
                       RatingRepository ratings, RegionCatalog regions) {
        this.deliveries = deliveries;
        this.payments = payments;
        this.farmerProfiles = farmerProfiles;
        this.buyerProfiles = buyerProfiles;
        this.ratings = ratings;
        this.regions = regions;
    }

    public OrderResponse toResponse(Order o, UUID viewerId, Role role) {
        boolean admin = role == Role.ADMIN;
        boolean isBuyer = o.getBuyer().getId().equals(viewerId);
        boolean isFarmer = o.getFarmer().getId().equals(viewerId);
        boolean isDriver = o.getDriver() != null && o.getDriver().getId().equals(viewerId);
        boolean contactsVisible = admin || o.getPaidAt() != null;

        FarmerProfile fp = farmerProfiles.findByUserId(o.getFarmer().getId()).orElse(null);
        BuyerProfile bp = buyerProfiles.findByUserId(o.getBuyer().getId()).orElse(null);
        PartyView buyer = party(o.getBuyer(), bp == null ? null : bp.getBusinessName(),
                admin || isBuyer || (contactsVisible && (isFarmer || isDriver)));
        PartyView farmer = party(o.getFarmer(), fp == null ? null : fp.getFarmName(),
                admin || isFarmer || (contactsVisible && (isBuyer || isDriver)));
        PartyView driver = o.getDriver() == null ? null
                : party(o.getDriver(), null, admin || isDriver || (contactsVisible && (isBuyer || isFarmer)));

        Delivery delivery = deliveries.findByOrderId(o.getId()).orElse(null);
        Payment payment = isDriver ? null
                : payments.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream().findFirst().orElse(null);

        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getStatusReason(), buyer, farmer,
                driver, o.getItems().stream().map(this::item).toList(),
                new Amounts(o.getSubtotalAmount(), o.getDeliveryFee(), o.getPlatformFee(), o.getTotalAmount(),
                        o.getCurrency()),
                o.getTotalWeightKg(), AddressDto.from(o.getDeliveryAddress(), regions), o.getDeliveryContactName(),
                o.getDeliveryContactPhone(),
                delivery == null ? null : AddressDto.from(delivery.getPickupAddress(), regions),
                o.getRequestedDeliveryDate(), o.getBuyerNotes(),
                new Deadlines(o.getFarmerResponseDeadline(), o.getPaymentDeadline(), o.getCheckWindowEndsAt()),
                new Timestamps(o.getCreatedAt(), o.getAcceptedAt(), o.getPaidAt(), o.getReadyAt(), o.getPickedUpAt(),
                        o.getDeliveredAt(), o.getCompletedAt(), o.getCancelledAt()),
                allowedActions(o, viewerId, role),
                payment == null ? null : new PaymentSummary(payment.getId(), payment.getStatus(), payment.getMethod(),
                        payment.getAmount(), payment.getFailureReason(), payment.getExpiresAt()),
                delivery == null ? null : deliverySummary(delivery, admin, isFarmer, isBuyer));
    }

    public OrderListItem toListItem(Order o, UUID viewerId, Role role) {
        String summary = o.getItems().stream()
                .map(i -> i.getProductName() + " " + i.getQuantity().stripTrailingZeros().toPlainString() + " "
                        + i.getUnit().name().toLowerCase())
                .collect(Collectors.joining(", "));
        return new OrderListItem(o.getId(), o.getOrderNumber(), o.getStatus(), summary, o.getItems().size(),
                o.getBuyer().getFullName(), o.getFarmer().getFullName(),
                o.getDriver() == null ? null : o.getDriver().getFullName(), o.getTotalAmount(), o.getCurrency(),
                o.getTotalWeightKg(), o.getDeliveryAddress().getTown(),
                new Deadlines(o.getFarmerResponseDeadline(), o.getPaymentDeadline(), o.getCheckWindowEndsAt()),
                o.getCreatedAt(), allowedActions(o, viewerId, role));
    }

    /**
     * Actions the viewer can take right now, so every client renders the same buttons without duplicating
     * workflow rules. Drivers act through delivery endpoints (see DeliveryResponse.allowedActions).
     */
    public List<String> allowedActions(Order o, UUID viewerId, Role role) {
        List<String> a = new ArrayList<>();
        OrderStatus s = o.getStatus();
        boolean isBuyer = o.getBuyer().getId().equals(viewerId);
        boolean isFarmer = o.getFarmer().getId().equals(viewerId);
        boolean isDriver = o.getDriver() != null && o.getDriver().getId().equals(viewerId);
        if (isFarmer) {
            if (s == OrderStatus.PENDING) {
                a.add("ACCEPT");
                a.add("REJECT");
            }
            if (s == OrderStatus.PAID) {
                a.add("MARK_READY");
            }
            if (s == OrderStatus.ACCEPTED || s == OrderStatus.PAYMENT_PENDING || s == OrderStatus.PAID
                    || s == OrderStatus.READY_FOR_PICKUP) {
                a.add("CANCEL");
            }
        }
        if (isBuyer) {
            if (s == OrderStatus.ACCEPTED) {
                a.add("PAY");
            }
            if (s == OrderStatus.PENDING || s == OrderStatus.ACCEPTED || s == OrderStatus.PAYMENT_PENDING
                    || s == OrderStatus.PAID) {
                a.add("CANCEL");
            }
            if (s == OrderStatus.DELIVERED) {
                a.add("CONFIRM_DELIVERY");
            }
        }
        if ((isBuyer || isFarmer || isDriver) && OrderStatus.DISPUTABLE.contains(s)) {
            a.add("REPORT_PROBLEM");
        }
        if (s == OrderStatus.COMPLETED && (isBuyer || isFarmer || isDriver)
                && ratings.countByOrderIdAndRaterId(o.getId(), viewerId)
                < com.agrilink.rating.RatingService.allowedTargets(role).size()) {
            a.add("RATE");
        }
        if (role == Role.ADMIN && EnumSetHolder.ADMIN_CANCELLABLE.contains(s)) {
            a.add("CANCEL");
        }
        return a;
    }

    private DeliverySummary deliverySummary(Delivery d, boolean admin, boolean isFarmer, boolean isBuyer) {
        boolean showPickup = admin || isFarmer;
        boolean showDelivery = admin || isBuyer;
        return new DeliverySummary(d.getId(), d.getStatus().name(), d.getDriverFee(), d.getDistanceKm(),
                d.getScheduledPickupDate(), showPickup ? d.getPickupCode() : null,
                showPickup ? d.getPickupQrToken() : null, showDelivery ? d.getDeliveryCode() : null,
                showDelivery ? d.getDeliveryQrToken() : null, d.getPickedUpWeightKg(), d.getPickedUpCrateCount(),
                d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt());
    }

    private OrderItemResponse item(OrderItem i) {
        return new OrderItemResponse(i.getId(), i.getListingId(), i.getProductId(), i.getProductName(),
                i.getQualityGrade(), i.getUnit(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal(), i.weightKg());
    }

    private PartyView party(User u, String displayName, boolean includePhone) {
        return new PartyView(u.getId(), u.getFullName(), displayName, includePhone ? u.getPhone() : null,
                u.getRatingAverage(), u.getRatingCount(), u.isVerified());
    }

    private static final class EnumSetHolder {
        static final java.util.Set<OrderStatus> ADMIN_CANCELLABLE = java.util.EnumSet.of(OrderStatus.PENDING,
                OrderStatus.ACCEPTED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, OrderStatus.READY_FOR_PICKUP);
    }
}
