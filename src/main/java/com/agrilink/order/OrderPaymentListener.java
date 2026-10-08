package com.agrilink.order;

import com.agrilink.payment.PaymentStatus;
import com.agrilink.payment.PaymentStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the order in step with its payment. Runs synchronously inside the payment transaction, so an order is
 * never PAID without its escrow being HELD or the reverse.
 */
@Component
public class OrderPaymentListener {

    private final OrderService orders;

    public OrderPaymentListener(OrderService orders) {
        this.orders = orders;
    }

    @EventListener
    public void on(PaymentStatusChangedEvent event) {
        switch (event.to()) {
            case PENDING -> orders.onPaymentInitiated(event.orderId());
            case HELD -> orders.onPaymentHeld(event.orderId());
            case FAILED -> orders.onPaymentFailed(event.orderId(), event.reason());
            default -> { }
        }
    }
}
