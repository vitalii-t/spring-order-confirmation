package com.order.confirmation.stripe;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MoneyFormatter {

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw",
            "mga", "pyg", "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf"
    );

    public String format(long amount, String currency) {
        String normalizedCurrency = currency == null ? "" : currency.toLowerCase();
        String upperCurrency = normalizedCurrency.toUpperCase();

        if (ZERO_DECIMAL_CURRENCIES.contains(normalizedCurrency)) {
            return upperCurrency + " " + amount;
        }

        return upperCurrency + " " + String.format("%.2f", amount / 100.0);
    }
}
