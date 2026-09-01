package com.ticketflow.api.pricing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [SPRING CORE - RECURSO PODEROSO] Injetar List<Interface> faz o Spring
 * entregar TODOS os beans que implementam aquela interface, automaticamente.
 *
 * Consequência: para adicionar uma política nova, você cria a classe com
 * @Component e PRONTO. Esta classe NUNCA muda. Nenhum registro, nenhum
 * cadastro, nenhum if. Isso é OCP levado ao limite.
 *
 * (Variante útil: Map<String, PricingPolicy> injeta todos indexados pelo
 *  NOME DO BEAN — bom quando você precisa escolher um por chave.)
 */
@Slf4j
@Service
public class PriceCalculator {
    private final List<PricingPolicy> policies;

    public PriceCalculator(List<PricingPolicy> policies){
        // Ordenar UMA VEZ no construtor, não a cada cálculo.
        // A lista fica efetivamente imutável -> thread-safe -> singleton seguro.
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(PricingPolicy::order))
                .toList();

        log.info("PriceCalculator initialized with {} policies: {}", policies.size(),
                this.policies.stream().map(PricingPolicy::code).toList());
    }

    /**
     * [CLEAN CODE] O método tem UM nível de abstração e 8 linhas.
     * Ele não conhece nenhuma regra de preço — só a ORQUESTRAÇÃO.
     */
    public PriceBreakdown calculate(PricingContext context){
        BigDecimal basePrice = context.event().getTicketPrice();
        BigDecimal currentPrice = basePrice;
        List<String> appliedPolicies = new ArrayList<>();

        for(PricingPolicy policy : policies){
            if(policy.appliesTo(context)){
                currentPrice = policy.apply(currentPrice, context);
                appliedPolicies.add(policy.code());
            }
        }

        BigDecimal unitPrice = currentPrice;
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(context.quantity()));

        return new PriceBreakdown(basePrice, unitPrice, total, appliedPolicies);
    }

    /** [CLEAN CODE] Retorne um objeto que EXPLICA o resultado, não só o número.
     *  O cliente quer ver "por que deu esse preço" — isso é requisito real. */
    public record PriceBreakdown(
            BigDecimal basePrice,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            List<String> appliedPolices
    ){}
}
