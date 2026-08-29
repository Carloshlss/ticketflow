package com.ticketflow.api.event.dto;

// [BEAN VALIDATION] jakarta.validation — a ESPECIFICAÇÃO.
// A implementação (Hibernate Validator) vem no spring-boot-starter-validation<.
import com.ticketflow.api.validation.EventIntervalAware;
import com.ticketflow.api.validation.ValidEventDuration;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * [DTO DE ENTRADA] Define o CONTRATO de escrita da API.
 *
 * Note o que NÃO está aqui e é intencional:
 *   id, status, availableTickets, createdAt, updatedAt, version
 * Esses campos são controlados pelo SERVIDOR. Não estarem no DTO torna
 * mass assignment impossível por construção — não depende de o dev lembrar
 * de ignorar campo nenhum.
 *
 * [JAVA 21 - RECORD] Imutável, o que é perfeito para um payload: nenhuma
 * camada pode alterar o que o cliente enviou.
 *
 * [BEAN VALIDATION] As anotações declaram INVARIANTES do contrato.
 * Elas só são checadas quando o parâmetro tem @Valid no controller.
 */
@ValidEventDuration
public record CreateEventRequest(

    // @NotBlank: não nulo, não vazio e não só espaços. (Para String, é o certo.)
    // @NotNull  -> só rejeita null ("" e "   " passam)
    // @NotEmpty -> rejeita null e "" ("   " passa)
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

    // @Future: a data deve ser posterior ao instante da validação.
    // Regra de negócio simples, declarada em vez de codificada. Menos if.
    @NotNull(message = "event.startsAt.required")
    @Future(message = "event.startsAt.future")
    Instant startsAt,

    @NotNull(message = "event.endsAt.required")
    @Future(message = "event.endsAt.future")
    Instant endsAt,

    // @DecimalMin em BigDecimal: nunca use @Min (é para inteiros).
    // @Digits espelha o NUMERIC(12,2) da migration: 10 inteiros + 2 decimais.
    @NotNull(message = "event.price.required")
    @DecimalMin(value = "0.0", message = "event.price.positive")
    @Digits(integer = 10, fraction = 2, message = "event.price.format")
    BigDecimal ticketPrice,

    @NotNull(message = "event.tickets.required")
    @Positive(message = "event.tickets.positive")
    @Max(value = 1_000_000, message = "event.tickets.max")
    Integer totalTickets
) implements EventIntervalAware {
    /**
     * [BEAN VALIDATION - @AssertTrue] Validação que envolve DOIS campos
     * (cross-field). Anotações de campo não conseguem fazer isso.
     *
     * O validador chama todo método público que retorna boolean e é
     * anotado com @AssertTrue. O nome da propriedade violada é derivado
     * do nome do método (isXxx -> "xxx").
     *
     * [CLEAN CODE] Mantém a regra JUNTO do contrato que ela protege,
     * em vez de espalhada num if dentro do service.
     */
    @AssertTrue(message = "event.dates.order")
    public boolean isEndDateAfterStartDate(){
        // null é tratado pelo @NotNull; aqui não é nosso trabalho.
        if(startsAt == null || endsAt == null){
            return true;
        }
        return endsAt.isAfter(startsAt);
    }
}
