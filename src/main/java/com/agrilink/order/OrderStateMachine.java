package com.agrilink.order;

import static com.agrilink.order.OrderStatus.ACCEPTED;
import static com.agrilink.order.OrderStatus.CANCELLED;
import static com.agrilink.order.OrderStatus.COMPLETED;
import static com.agrilink.order.OrderStatus.DELIVERED;
import static com.agrilink.order.OrderStatus.DISPUTED;
import static com.agrilink.order.OrderStatus.EXPIRED;
import static com.agrilink.order.OrderStatus.IN_TRANSIT;
import static com.agrilink.order.OrderStatus.PAID;
import static com.agrilink.order.OrderStatus.PAYMENT_PENDING;
import static com.agrilink.order.OrderStatus.PENDING;
import static com.agrilink.order.OrderStatus.PICKED_UP;
import static com.agrilink.order.OrderStatus.READY_FOR_PICKUP;
import static com.agrilink.order.OrderStatus.REJECTED;

import com.agrilink.common.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** The single source of truth for which order status changes are legal. */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(ACCEPTED, REJECTED, CANCELLED, EXPIRED));
        ALLOWED.put(ACCEPTED, EnumSet.of(PAYMENT_PENDING, CANCELLED, EXPIRED));
        // Failed payments return to ACCEPTED so the buyer can retry until the payment deadline.
        ALLOWED.put(PAYMENT_PENDING, EnumSet.of(PAID, ACCEPTED, CANCELLED, EXPIRED));
        // A driver's pickup code scan is proof of handover even if the farmer never tapped "ready".
        ALLOWED.put(PAID, EnumSet.of(READY_FOR_PICKUP, PICKED_UP, CANCELLED, DISPUTED));
        ALLOWED.put(READY_FOR_PICKUP, EnumSet.of(PICKED_UP, CANCELLED, DISPUTED));
        ALLOWED.put(PICKED_UP, EnumSet.of(IN_TRANSIT, DELIVERED, DISPUTED));
        ALLOWED.put(IN_TRANSIT, EnumSet.of(DELIVERED, DISPUTED));
        ALLOWED.put(DELIVERED, EnumSet.of(COMPLETED, DISPUTED));
        ALLOWED.put(DISPUTED, EnumSet.of(COMPLETED, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(EXPIRED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<OrderStatus> nextStates(OrderStatus from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }

    public static void assertTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw ApiException.invalidTransition("Order cannot move from " + from + " to " + to);
        }
    }
}
