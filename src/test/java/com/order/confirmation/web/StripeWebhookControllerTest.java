package com.order.confirmation.web;

import com.order.confirmation.config.CacheConfig;
import com.order.confirmation.email.OrderConfirmationEmailSender;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.stripe.secret-key=sk_test_123",
        "app.stripe.webhook-secret=whsec_test_secret",
        "app.mailtrap.api-token=test_mailtrap_token",
        "app.mailtrap.template-uuid=test_template_uuid",
        "app.mailtrap.from-email=orders@example.com",
        "app.mailtrap.fallback-recipient=fallback@example.com"
})
@AutoConfigureMockMvc
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private OrderConfirmationEmailSender emailSender;

    @BeforeEach
    void clearCache() {
        cacheManager.getCache(CacheConfig.STRIPE_EVENT_CACHE).clear();
    }

    @Test
    void rejectsTamperedPayload() throws Exception {
        String originalPayload = checkoutPayload("evt_tampered");
        String tamperedPayload = "{\"id\":\"evt_tampered\",\"type\":\"checkout.session.completed\"}";

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", stripeSignature(originalPayload))
                        .content(tamperedPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid signature"));
    }

    @Test
    void sendsOrderConfirmationFromWebhookPayload() throws Exception {
        String payload = checkoutPayload("evt_success");

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", stripeSignature(payload))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        ArgumentCaptor<com.order.confirmation.order.OrderConfirmation> orderCaptor =
                ArgumentCaptor.forClass(com.order.confirmation.order.OrderConfirmation.class);
        verify(emailSender).send(anyString(), orderCaptor.capture());

        assertThat(orderCaptor.getValue().items().getFirst().name()).isEqualTo("Widget Pro");
    }

    @Test
    void skipsDuplicateEventGracefully() throws Exception {
        String eventId = "evt_duplicate";
        cacheManager.getCache(CacheConfig.STRIPE_EVENT_CACHE).put(eventId, true);
        String payload = checkoutPayload(eventId);

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", stripeSignature(payload))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("already processed"));

        verifyNoInteractions(emailSender);
    }

    @Test
    void returns500WhenMailDeliveryFails() throws Exception {
        doThrow(new RuntimeException("Mailtrap unavailable"))
                .when(emailSender).send(anyString(), any());

        String payload = checkoutPayload("evt_mail_fail");

        mockMvc.perform(post("/api/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", stripeSignature(payload))
                        .content(payload))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Processing failed"));

        assertThat(cacheManager.getCache(CacheConfig.STRIPE_EVENT_CACHE).get("evt_mail_fail")).isNull();
    }

    private String stripeSignature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + toHex(digest);
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String checkoutPayload(String eventId) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "object": "checkout.session",
                      "amount_total": 4999,
                      "currency": "usd",
                      "customer_details": {
                        "email": "buyer@example.com"
                      },
                      "shipping_details": {
                        "name": "Jane Doe",
                        "address": {
                          "line1": "123 Main St",
                          "line2": "Apt 4",
                          "city": "Springfield",
                          "state": "IL",
                          "postal_code": "62704",
                          "country": "US"
                        }
                      },
                      "line_items": {
                        "data": [
                          {
                            "description": "Widget Pro",
                            "quantity": 2,
                            "amount_total": 3998,
                            "currency": "usd"
                          },
                          {
                            "description": "Shipping",
                            "quantity": 1,
                            "amount_total": 1001,
                            "currency": "usd"
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(eventId);
    }
}
