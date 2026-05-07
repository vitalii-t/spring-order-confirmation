package com.order.confirmation.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.order.confirmation.order.OrderConfirmation;
import com.order.confirmation.order.OrderItem;
import com.order.confirmation.order.ShippingAddress;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderPayloadParser {

    private final MoneyFormatter moneyFormatter;

    public OrderPayloadParser(MoneyFormatter moneyFormatter) {
        this.moneyFormatter = moneyFormatter;
    }

    public OrderConfirmation parse(JsonNode sessionNode) {
        List<OrderItem> items = new ArrayList<>();
        JsonNode lineItems = sessionNode.path("line_items").path("data");
        if (lineItems.isArray()) {
            for (JsonNode lineItem : lineItems) {
                items.add(new OrderItem(
                        text(lineItem, "description", text(lineItem.path("price").path("product"), "name", "Item")),
                        lineItem.path("quantity").asLong(0),
                        moneyFormatter.format(lineItem.path("amount_total").asLong(0), text(lineItem, "currency", "usd"))
                ));
            }
        }

        return new OrderConfirmation(
                text(sessionNode, "id", ""),
                items,
                moneyFormatter.format(sessionNode.path("amount_total").asLong(0), text(sessionNode, "currency", "usd")),
                shippingAddress(sessionNode.path("shipping_details"))
        );
    }

    public String extractRecipientEmail(JsonNode sessionNode) {
        return text(sessionNode.path("customer_details"), "email", null);
    }

    public boolean hasLineItems(JsonNode sessionNode) {
        return sessionNode.path("line_items").path("data").isArray();
    }

    private ShippingAddress shippingAddress(JsonNode shippingNode) {
        if (shippingNode.isMissingNode() || shippingNode.isNull()) {
            return null;
        }

        JsonNode addressNode = shippingNode.path("address");
        return new ShippingAddress(
                text(shippingNode, "name", ""),
                text(addressNode, "line1", ""),
                text(addressNode, "line2", ""),
                text(addressNode, "city", ""),
                text(addressNode, "state", ""),
                text(addressNode, "postal_code", ""),
                text(addressNode, "country", "")
        );
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText();
    }
}
