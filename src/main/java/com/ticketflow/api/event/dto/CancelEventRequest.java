package com.ticketflow.api.event.dto;

import jakarta.validation.constraints.Size;

/**
 * [API REST] Corpo do POST /events/{id}/cancel.
 *
 * Por que um record e não @RequestBody String:
 *   1. JSON de objeto é o contrato esperado de uma API REST
 *   2. extensível: amanhã entram refundImmediately, notifyCustomers, sem
 *      quebrar nenhum cliente existente
 *   3. validável com Bean Validation
 *   4. autodocumentado no Swagger com nome de campo
 *
 * 'reason' é opcional aqui (sem @NotBlank) porque a obrigatoriedade é
 * CONDICIONAL (só perto do início). Isso é regra de NEGÓCIO, e pertence à
 * EventCancellationPolicy, não ao Bean Validation — que não conhece o evento.
 * Distinção importante: o DTO valida FORMATO; a policy valida REGRA.
 */
public record CancelEventRequest(
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {
}
