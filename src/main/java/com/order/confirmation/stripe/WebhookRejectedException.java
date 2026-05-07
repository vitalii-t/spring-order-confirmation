package com.order.confirmation.stripe;

public class WebhookRejectedException extends RuntimeException {

    public WebhookRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
