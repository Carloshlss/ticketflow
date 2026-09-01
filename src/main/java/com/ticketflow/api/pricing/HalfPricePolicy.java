package com.ticketflow.api.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * [OCP] Política 1. Note: para adicionar isto ao sistema, NENHUM arquivo
 * existente foi modificado. Só criamos um arquivo novo com @Component.
 */
@Component
public class HalfPricePolicy implements PricingPolicy {

    // [CLEAN CODE] Constante nomeada em vez de "magic number" 0.5 no meio do código.
    // O nome explica a REGRA; o valor é só o detalhe.
    private static final BigDecimal HALF = new BigDecimal("0.50");

    @Override
    public boolean appliesTo(PricingContext context) {
        return context.customerType() == PricingContext.CustomerType.STUDENT
                || context.customerType() == PricingContext.CustomerType.SENIOR;
    }

    // ⚠️ BigDecimal é IMUTÁVEL: multiply() retorna novo objeto, não altera.
    // E toda divisão/arredondamento EXIGE RoundingMode explícito —
    // sem ele, divisão não exata lança ArithmeticException.
    @Override
    public BigDecimal apply(BigDecimal basePrice, PricingContext context) {
        return basePrice.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public int order(){
        return 10;   // descontos primeiro
    }

    @Override
    public String code() {
        return "HALF_PRICE";
    }
}
