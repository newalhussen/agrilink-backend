package com.agrilink.delivery;

import com.agrilink.order.OrderRepository;
import com.agrilink.order.OrderStatus;
import com.agrilink.order.OrderStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reacts to order status changes: posts the job when paid, closes it when the order is cancelled. */
@Component
public class DeliveryOrderListener {

    private final DeliveryService deliveries;
    private final OrderRepository orders;

    public DeliveryOrderListener(DeliveryService deliveries, OrderRepository orders) {
        this.deliveries = deliveries;
        this.orders = orders;
    }

    @EventListener
    public void on(OrderStatusChangedEvent event) {
        switch (event.to()) {
            case PAID -> orders.findWithDetailsById(event.orderId()).ifPresent(deliveries::createForOrder);
            case CANCELLED -> deliveries.cancelForOrder(event.orderId(), event.reason());
            case COMPLETED -> deliveries.onOrderCompleted(event.orderId());
            default -> { }
        }
    }
}
