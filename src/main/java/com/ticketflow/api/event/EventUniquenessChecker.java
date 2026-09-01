package com.ticketflow.api.event;

import com.ticketflow.api.shared.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * [SRP] Responsabilidade ÚNICA: garantir que nome de evento não se repita.
 *
 * "Uma classe de 20 linhas para isso?" Sim, e vale. Motivos:
 *   1. a regra tem DUAS variantes sutis (criar vs atualizar) e a segunda
 *      é fácil de errar — isolada, ela é obviamente correta
 *   2. testável sozinha, sem subir nada
 *   3. o EventCommandService fica legível: assertNameIsAvailable(name)
 *      é uma linha que se lê como português
 *
 * [CLEAN CODE] Prefixo "assert": comunica que o método não retorna nada e
 * LANÇA se a condição falhar. O leitor não precisa checar o retorno.
 */
@Component
@RequiredArgsConstructor
public class EventUniquenessChecker {
    private final EventRepository eventRepository;

    /**
     * ⚠️ Este check é TOCTOU (time-of-check to time-of-use): duas requisições
     * simultâneas podem passar aqui e ambas inserir. A garantia REAL é o
     * índice único do banco (sua migration V4).
     *
     * Então por que existir? Para dar uma mensagem ESPECÍFICA no caso comum
     * ("Event already exists with name: X") em vez do genérico
     * DATA_INTEGRITY_VIOLATION. Camadas com propósitos diferentes:
     *   aplicação -> boa experiência
     *   banco     -> garantia
     */
    public void assertNameIsAvailable(String name){
        if(eventRepository.existsByNameIgnoreCase(name)){
            throw DuplicateResourceException.of("Event", "name", name);
        }
    }

    /**
     * Variante para update: o próprio evento pode manter seu nome.
     * Sem o filter por id, atualizar um evento sem mudar o nome falharia —
     * bug clássico e chato de diagnosticar.
     */
    public void assertNameIsAvailableForUpdate(String name, Long currentEventId){
        eventRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentEventId))
                .ifPresent(existing -> {
                    throw DuplicateResourceException.of("Event", "name", name);
                });
    }
}