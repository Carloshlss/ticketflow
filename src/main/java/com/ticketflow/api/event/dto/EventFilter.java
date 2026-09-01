package com.ticketflow.api.event.dto;

import com.ticketflow.api.event.EventStatus;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * [CLEAN CODE - PARAMETER OBJECT] Todos os filtros num objeto.
 * Sem isto, o método do controller teria 7 @RequestParam soltos.
 *
 * [SPRING MVC] Sem @RequestBody, o Spring popula um record a partir dos
 * QUERY PARAMS automaticamente (data binding por nome). Então
 *   GET /events/search?city=Sao Paulo&minPrice=100&search=rock
 * chega aqui montado. Zero código de parsing.
 */
public record EventFilter(
        String city,
        EventStatus status,
        @DecimalMin("0.0") BigDecimal minPrice,
        @DecimalMin("0.0") BigDecimal maxPrice,
        Instant startsFrom,
        Instant startsTo,
        String search,
        Boolean onlyAvailable,
        String organizerName
        ) {
    /** [CLEAN CODE] Trata o Boolean nulo num só lugar, evitando NPE espalhado. */
    public boolean isOnlyAvailable(){
        return Boolean.TRUE.equals(onlyAvailable);
    }

    /**
     * [CLEAN CODE] Regra de coerência do próprio filtro, junto do filtro.
     * Chamada pelo service (não é Bean Validation porque queremos a
     * mensagem de negócio, não um 400 genérico).
     */
    public boolean hasInvalidPriceRange() {
        return minPrice != null && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0;
    }
}
