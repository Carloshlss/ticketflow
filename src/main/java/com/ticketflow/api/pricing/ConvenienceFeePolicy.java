package com.ticketflow.api.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** [OCP] Política 3: taxa de conveniência, aplicada DEPOIS dos descontos. */
@Component
public class ConvenienceFeePolicy implements PricingPolicy {
private static final BigDecimal FEE_RATE = new BigDecimal("0.10");

    @Override
    public boolean appliesTo(PricingContext context) {
        return true;
    }

    @Override
    public BigDecimal apply(BigDecimal basePrice, PricingContext context) {
        BigDecimal fee = basePrice.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        return basePrice.add(fee);
    }

    @Override
    public int order() {
        return 100;   // por último
    }

    @Override
    public String code() {
        return "CONVENIENCE_FEE";
    }
}
