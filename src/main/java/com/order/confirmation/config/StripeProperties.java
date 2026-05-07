package com.order.confirmation.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.stripe")
public record StripeProperties(
        @NotBlank String secretKey,
        @NotBlank String webhookSecret
) {
}
