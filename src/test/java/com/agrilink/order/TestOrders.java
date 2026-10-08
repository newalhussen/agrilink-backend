package com.agrilink.order;

import com.agrilink.TestSupport;

/** Test-only access to package-private order state, so other packages' tests can build orders in any status. */
public final class TestOrders {

    private TestOrders() {
    }

    public static void setStatus(Order order, OrderStatus status) {
        order.applyStatus(status, TestSupport.NOW, null);
    }
}
