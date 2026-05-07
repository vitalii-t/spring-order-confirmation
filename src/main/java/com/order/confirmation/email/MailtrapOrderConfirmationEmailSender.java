package com.order.confirmation.email;

import com.order.confirmation.config.MailtrapProperties;
import com.order.confirmation.order.OrderConfirmation;
import io.mailtrap.client.MailtrapClient;
import io.mailtrap.model.request.emails.Address;
import io.mailtrap.model.request.emails.MailtrapMail;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MailtrapOrderConfirmationEmailSender implements OrderConfirmationEmailSender {

    private final MailtrapClient mailtrapClient;
    private final MailtrapProperties mailtrapProperties;

    public MailtrapOrderConfirmationEmailSender(
        MailtrapClient mailtrapClient,
        MailtrapProperties mailtrapProperties
    ) {
        this.mailtrapClient = mailtrapClient;
        this.mailtrapProperties = mailtrapProperties;
    }

    @Override
    public void send(String recipientEmail, OrderConfirmation orderConfirmation) {
        MailtrapMail mail = MailtrapMail.builder()
            .from(new Address(mailtrapProperties.fromEmail(), mailtrapProperties.fromName()))
            .to(List.of(new Address(recipientEmail)))
            .templateUuid(mailtrapProperties.templateUuid())
            .templateVariables(Map.of(
                "order_id", orderConfirmation.orderId(),
                "items", orderConfirmation.items(),
                "total", orderConfirmation.total(),
                "shipping_address", orderConfirmation.shippingAddress() != null ? orderConfirmation.shippingAddress() : ""
            ))
            .build();

        mailtrapClient.send(mail);
    }
}
