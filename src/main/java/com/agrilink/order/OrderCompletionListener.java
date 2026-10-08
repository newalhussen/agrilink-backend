package com.agrilink.order;

import com.agrilink.buyer.BuyerProfileRepository;
import com.agrilink.farmer.FarmerProfileRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Updates the "trades completed" counters shown on trust profiles when an order completes. */
@Component
public class OrderCompletionListener {

    private final FarmerProfileRepository farmerProfiles;
    private final BuyerProfileRepository buyerProfiles;

    public OrderCompletionListener(FarmerProfileRepository farmerProfiles, BuyerProfileRepository buyerProfiles) {
        this.farmerProfiles = farmerProfiles;
        this.buyerProfiles = buyerProfiles;
    }

    @EventListener(condition = "#event.to == T(com.agrilink.order.OrderStatus).COMPLETED")
    public void on(OrderStatusChangedEvent event) {
        farmerProfiles.findByUserId(event.farmerId()).ifPresent(p -> p.incrementCompletedTrades());
        buyerProfiles.findByUserId(event.buyerId()).ifPresent(p -> p.incrementCompletedOrders());
    }
}
