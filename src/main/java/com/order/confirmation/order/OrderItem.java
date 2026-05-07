package com.order.confirmation.order;

public record OrderItem(
        String name,
        long quantity,
        String price
) {
}
