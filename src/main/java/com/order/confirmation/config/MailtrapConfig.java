package com.order.confirmation.config;

import io.mailtrap.client.MailtrapClient;
import io.mailtrap.config.MailtrapConfig.Builder;
import io.mailtrap.factory.MailtrapClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailtrapConfig {

    @Bean
    MailtrapClient mailtrapClient(MailtrapProperties mailtrapProperties) {
        io.mailtrap.config.MailtrapConfig.Builder builder = new io.mailtrap.config.MailtrapConfig.Builder()
            .token(mailtrapProperties.apiToken());

        if (mailtrapProperties.sandbox()) {
            builder.sandbox(true);
            if (mailtrapProperties.inboxId() != null) {
                builder.inboxId(mailtrapProperties.inboxId());
            }
        }

        return MailtrapClientFactory.createMailtrapClient(builder.build());
    }
}
