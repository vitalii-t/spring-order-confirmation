package com.order.confirmation.stripe;

public class WebhookProcessingException extends RuntimeException {

    public WebhookProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
