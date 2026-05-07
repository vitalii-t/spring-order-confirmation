package com.order.confirmation.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.confirmation.config.StripeProperties;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionRetrieveParams;
import org.springframework.stereotype.Component;

@Component
public class StripeSessionEnricher {

    private final ObjectMapper objectMapper;
    private final StripeProperties stripeProperties;

    public StripeSessionEnricher(ObjectMapper objectMapper, StripeProperties stripeProperties) {
        this.objectMapper = objectMapper;
        this.stripeProperties = stripeProperties;
    }

    public JsonNode enrich(JsonNode sessionNode) {
        if (sessionNode.path("id").isMissingNode() || sessionNode.path("id").isNull()) {
            return sessionNode;
        }

        if (sessionNode.path("line_items").path("data").isArray()) {
            return sessionNode;
        }

        try {
            SessionRetrieveParams params = SessionRetrieveParams.builder()
                    .addExpand("line_items")
                    .build();
            RequestOptions requestOptions = RequestOptions.builder()
                    .setApiKey(stripeProperties.secretKey())
                    .build();
            Session session = Session.retrieve(sessionNode.path("id").asText(), params, requestOptions);
            return objectMapper.readTree(session.toJson());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new WebhookProcessingException("Unable to map Stripe checkout session JSON", exception);
        } catch (StripeException exception) {
            throw new WebhookProcessingException("Unable to retrieve checkout session from Stripe", exception);
        }
    }
}
