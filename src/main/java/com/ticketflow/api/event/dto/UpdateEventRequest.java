package com.ticketflow.api.event.dto;

import com.ticketflow.api.validation.EventIntervalAware;
import com.ticketflow.api.validation.ValidEventDuration;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

@ValidEventDuration
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
        @NotBlank(message = "event.name.required")
        @Size(min = 3, max = 150, message = "event.name.size")
        String name,

        @Size(max = 5000, message = "event.description.size")
        String description,

        @NotBlank(message = "event.venue.required")
        @Size(max = 200)
        String venue,

        @NotBlank(message = "event.city.required")
        @Size(max = 100)
        String city,

        @NotBlank(message = "event.organizer.required")
        @Size(max = 50)
        String organizerName,

        @NotNull(message = "event.startsAt.required")
        @Future(message = "event.startsAt.future")
        Instant startsAt,

        @NotNull(message = "event.endsAt.required")
        @Future(message = "event.endsAt.future")
        Instant endsAt,

        @NotNull(message = "event.price.required")
        @DecimalMin(value = "0.0", message = "event.price.positive")
        @Digits(integer = 10, fraction = 2, message = "event.price.format")
        BigDecimal ticketPrice
) implements EventIntervalAware {
    @AssertTrue(message = "event.dates.order")
    public boolean isEndDateAfterStartDate(){
        if(startsAt == null || endsAt == null) return true;
        return endsAt.isAfter(startsAt);
    }
}
