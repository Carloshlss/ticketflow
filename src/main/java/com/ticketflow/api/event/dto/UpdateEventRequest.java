package com.ticketflow.api.event.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * [DTO DE ENTRADA - UPDATE] Por que não reaproveitar o CreateEventRequest?
 *
 * Porque os contratos DIVERGEM. Aqui não existe totalTickets: alterar o total
 * de ingressos de um evento com vendas em andamento é uma operação de negócio
 * distinta (afeta availableTickets, pode causar overbooking) e merecerá um
 * endpoint próprio.
 *
 * [SOLID - Single Responsibility] Um DTO por caso de uso. É "duplicação"
 * aparente, mas os dois evoluem em direções diferentes. Unificar hoje
 * significa criar campos opcionais e ifs de contexto amanhã.
 *
 * [API REST] Este DTO é para PUT (substituição total do recurso), por isso
 * todos os campos são obrigatórios. Se fosse PATCH (parcial), os campos
 * seriam opcionais e precisaríamos distinguir "não enviado" de "enviado null".
 */
public record UpdateEventRequest(
        @NotBlank
        @Size(min = 3, max = 150)
        String name,

        @Size(max = 5000)
        String description,

        @NotBlank
        @Size(max = 200)
        String venue,

        @NotBlank
        @Size(max = 100)
        String city,

        @NotBlank
        @Size(max = 50)
        String organizerName,

        @NotNull
        @Future
        Instant startsAt,

        @NotNull
        @Future
        Instant endsAt,

        @NotNull
        @DecimalMin("0.0")
        @Digits(integer = 10, fraction = 2)
        BigDecimal ticketPrice
) {
    @AssertTrue(message = "End date must be after start date")
    public boolean isEndDateAfterStartDate(){
        if(startsAt == null || endsAt == null) return true;
        return endsAt.isAfter(startsAt);
    }
}
