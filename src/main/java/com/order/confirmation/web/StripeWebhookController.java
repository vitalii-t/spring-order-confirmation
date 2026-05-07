package com.order.confirmation.web;

import com.order.confirmation.stripe.StripeWebhookResponse;
import com.order.confirmation.stripe.StripeWebhookService;
import com.order.confirmation.stripe.WebhookProcessingException;
import com.order.confirmation.stripe.WebhookRejectedException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    public StripeWebhookController(StripeWebhookService stripeWebhookService) {
        this.stripeWebhookService = stripeWebhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<StripeWebhookResponse> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", defaultValue = "") String signatureHeader
    ) {
        return ResponseEntity.ok(stripeWebhookService.handle(payload, signatureHeader));
    }

    @ExceptionHandler(WebhookRejectedException.class)
    public ResponseEntity<Map<String, String>> handleRejected(WebhookRejectedException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid signature"));
    }

    @ExceptionHandler(WebhookProcessingException.class)
    public ResponseEntity<Map<String, String>> handleProcessingFailure(WebhookProcessingException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Processing failed"));
    }
}
