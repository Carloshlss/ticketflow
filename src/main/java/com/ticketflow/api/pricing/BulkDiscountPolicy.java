package com.ticketflow.api.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** [OCP] Política 2. Outro arquivo novo, zero modificação. */
@Component
public class BulkDiscountPolicy implements PricingPolicy {
    private static final BigDecimal BULK_MULTIPLIER = new BigDecimal("0.90");

    @Override
    public boolean appliesTo(PricingContext context) {
        return context.isBulkPurchase();
    }

    @Override
    public BigDecimal apply(BigDecimal basePrice, PricingContext context) {
        return basePrice.multiply(BULK_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String code() {
        return "BULK_DISCOUNT";
    }
}
