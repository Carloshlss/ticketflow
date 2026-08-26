package com.ticketflow.api.event.dto;

import com.ticketflow.api.event.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * [DTO DE SAÍDA] Representação COMPLETA do evento (GET /events/{id}).
 *
 * Diferenças em relação à entidade:
 *   - expõe 'id' e 'status' (leitura é segura; escrita não seria)
 *   - NÃO expõe 'version' (detalhe interno de concorrência)
 *   - ADICIONA campos calculados: soldTickets, soldOut, occupancyRate
 *
 * Esses campos derivados são o melhor argumento a favor do DTO: a API entrega
 * o que o front precisa, sem forçar o Angular a fazer conta nem obrigar o
 * banco a ter coluna redundante.
 */
public record EventResponse(
        Long id,
        String name,
        String description,
        String venue,
        String city,
        String organizerName,
        Instant startsAt,
        Instant endsAt,
        BigDecimal ticketPrice,
        Integer totalTickets,
        Integer availableTickets,

        // CAMPOS CALCULADOS — não existem no banco
        Integer soldTickets,
        Double occupancyRate,
        boolean solgOut,

        EventStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
