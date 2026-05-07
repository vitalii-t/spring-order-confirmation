package com.order.confirmation.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.mailtrap")
public record MailtrapProperties(
        @NotBlank String apiToken,
        @NotBlank String templateUuid,
        @NotBlank String fromEmail,
        String fromName,
        String fallbackRecipient,
        boolean sandbox,
        Long inboxId
) {
}
