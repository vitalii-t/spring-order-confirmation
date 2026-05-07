package com.order.confirmation.order;

import java.util.List;

public record OrderConfirmation(
        String orderId,
        List<OrderItem> items,
        String total,
        ShippingAddress shippingAddress
) {
}
