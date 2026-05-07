package com.order.confirmation.config;

import com.stripe.StripeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Bean
    StripeClient stripeClient(StripeProperties stripeProperties) {
        return new StripeClient(stripeProperties.secretKey());
    }
}
