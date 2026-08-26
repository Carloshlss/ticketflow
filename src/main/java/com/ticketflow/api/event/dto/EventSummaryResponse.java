package com.ticketflow.api.event.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * [DTO DE SAÍDA - RESUMO] Versão enxuta para LISTAGEM.
 *
 * Por que dois DTOs de saída? Porque a vitrine do Angular mostra 20 cards
 * com nome, cidade, data e preço. Mandar 'description' de 5000 caracteres
 * × 20 itens é desperdício de banda e de memória.
 *
 * [PERFORMANCE] Na Fase 5 vamos além: uma PROJEÇÃO no repositório, para que
 * o próprio SELECT traga só estas colunas, em vez de carregar a entidade
 * inteira e depois descartar campos.
 */
public record EventSummaryResponse(
        Long id,
        String name,
        String venue,
        String city,
        Instant startsAt,
        BigDecimal ticketPrice,
        Integer availableTickets,
        boolean soldOut
) {
}
