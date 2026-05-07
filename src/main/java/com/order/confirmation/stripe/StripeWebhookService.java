package com.order.confirmation.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.confirmation.config.CacheConfig;
import com.order.confirmation.config.MailtrapProperties;
import com.order.confirmation.config.StripeProperties;
import com.order.confirmation.email.OrderConfirmationEmailSender;
import com.order.confirmation.order.OrderConfirmation;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class StripeWebhookService {

    private final ObjectMapper objectMapper;
    private final StripeProperties stripeProperties;
    private final MailtrapProperties mailtrapProperties;
    private final Cache eventCache;
    private final OrderPayloadParser orderPayloadParser;
    private final StripeSessionEnricher stripeSessionEnricher;
    private final OrderConfirmationEmailSender emailSender;

    public StripeWebhookService(
            ObjectMapper objectMapper,
            StripeProperties stripeProperties,
            MailtrapProperties mailtrapProperties,
            CacheManager cacheManager,
            OrderPayloadParser orderPayloadParser,
            StripeSessionEnricher stripeSessionEnricher,
            OrderConfirmationEmailSender emailSender
    ) {
        this.objectMapper = objectMapper;
        this.stripeProperties = stripeProperties;
        this.mailtrapProperties = mailtrapProperties;
        this.eventCache = cacheManager.getCache(CacheConfig.STRIPE_EVENT_CACHE);
        this.orderPayloadParser = orderPayloadParser;
        this.stripeSessionEnricher = stripeSessionEnricher;
        this.emailSender = emailSender;
    }

    public StripeWebhookResponse handle(String payload, String signatureHeader) {
        Event event = verify(payload, signatureHeader);

        if (isDuplicate(event.getId())) {
            return new StripeWebhookResponse("already processed");
        }

        if (!"checkout.session.completed".equals(event.getType())) {
            markProcessed(event.getId());
            return new StripeWebhookResponse("ok");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode sessionNode = stripeSessionEnricher.enrich(root.path("data").path("object"));
            OrderConfirmation orderConfirmation = orderPayloadParser.parse(sessionNode);

            String recipientEmail = orderPayloadParser.extractRecipientEmail(sessionNode);
            if (recipientEmail == null || recipientEmail.isBlank()) {
                recipientEmail = mailtrapProperties.fallbackRecipient();
            }

            if (recipientEmail == null || recipientEmail.isBlank()) {
                markProcessed(event.getId());
                return new StripeWebhookResponse("ok");
            }

            emailSender.send(recipientEmail, orderConfirmation);
            markProcessed(event.getId());
            return new StripeWebhookResponse("ok");
        } catch (WebhookProcessingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new WebhookProcessingException("Unable to process checkout.session.completed", exception);
        }
    }

    private Event verify(String payload, String signatureHeader) {
        try {
            return Webhook.constructEvent(payload, signatureHeader, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException | IllegalArgumentException exception) {
            throw new WebhookRejectedException("Invalid Stripe webhook signature or payload", exception);
        }
    }

    private boolean isDuplicate(String eventId) {
        return eventCache.get(eventId, Boolean.class) != null;
    }

    private void markProcessed(String eventId) {
        eventCache.put(eventId, true);
    }
}
