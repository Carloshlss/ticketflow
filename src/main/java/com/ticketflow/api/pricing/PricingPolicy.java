package com.ticketflow.api.pricing;

import java.math.BigDecimal;

public interface PricingPolicy {
    boolean appliesTo(PricingContext context);

    BigDecimal apply(BigDecimal basePrice, PricingContext context);

    default int order(){
        return 0;
    }

    String code();
}
