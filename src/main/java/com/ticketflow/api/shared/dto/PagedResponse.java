package com.ticketflow.api.shared.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * [DTO GENÉRICO] Envelope de paginação PRÓPRIO. Por que não devolver o
 * Page<T> do Spring Data direto?
 *
 * 1. O JSON do PageImpl é instável entre versões do Spring Data — e, a partir
 *    do Spring Boot 3.3, ele até emite WARNING no log quando serializado.
 * 2. Ele vaza estrutura interna do framework (campos "pageable", "sort.sorted",
 *    "unpaged"...) para o contrato público da sua API.
 * 3. Aqui você controla exatamente o formato que o Angular vai consumir.
 *
 * [JAVA GENERICS] <T> deixa isso reutilizável para qualquer recurso:
 * PagedResponse<EventSummaryResponse>, PagedResponse<OrderResponse>...
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
    /**
     * [FACTORY METHOD estático + FUNÇÃO DE MAPEAMENTO]
     * Recebe o Page<Entidade> do Spring Data e um Function que converte
     * cada item para DTO. Concentra a tradução num único lugar.
     *
     * Function<E, T> é interface funcional do Java 8 — o mapper entra
     * como method reference: PagedResponse.from(page, mapper::toSummary)
     */
    public static <E,T> PagedResponse<T> from(Page<E> page, Function<E,T> converter){
        return new PagedResponse<>(
                page.getContent().stream().map(converter).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
