package com.order.confirmation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        StripeProperties.class,
        MailtrapProperties.class
})
public class AppPropertiesConfig {
}
