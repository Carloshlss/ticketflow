package com.ticketflow.api.event.dto;

/**
 * [SPRING DATA - PROJEÇÃO POR INTERFACE] O Spring Data cria um proxy que mapeia
 * os aliases da query para os getters. Tipado, legível, sem cast de Object[].
 *
 * Regra: o alias no SQL precisa casar com o nome do getter
 * (getCity -> alias "city"). Em query nativa, o alias é obrigatório.
 */
public interface CityEventCount {
    String getCity();
    Long getTotal();
}
