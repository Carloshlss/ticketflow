package com.ticketflow.api.pricing;

import com.ticketflow.api.event.Event;

import java.time.Instant;

/**
 * [CLEAN CODE - PARAMETER OBJECT] Em vez de um método com 5 parâmetros soltos
 * (customerType, quantity, purchaseDate, coupon, isFirstPurchase), agrupamos
 * num objeto. Ganhos:
 *   - adicionar um dado novo NÃO muda a assinatura de todas as políticas
 *   - impossível trocar a ordem de dois parâmetros do mesmo tipo por acidente
 *   - o record é imutável, então nenhuma política pode alterar o contexto
 */
public record PricingContext(
        Event event,
        CustomerType customerType,
        int quantity,
        Instant purchaseDate,
        String couponCode
) {
    public enum CustomerType {
        REGULAR,
        STUDENT,
        SENIOR,
        CORPORATE
    }

    /** [CLEAN CODE] Consulta com nome de intenção, em vez de o cliente calcular. */
    public boolean isBulkPurchase(){
        return quantity >= 10;
    }

    public boolean hasCoupon(){
        return couponCode != null && !couponCode.isBlank();
    }
}
