package com.order.confirmation.email;

import com.order.confirmation.order.OrderConfirmation;

public interface OrderConfirmationEmailSender {

    void send(String recipientEmail, OrderConfirmation orderConfirmation);
}
