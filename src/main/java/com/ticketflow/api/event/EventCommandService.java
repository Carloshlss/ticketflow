package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.CreateEventRequest;
import com.ticketflow.api.event.dto.EventResponse;
import com.ticketflow.api.event.dto.UpdateEventRequest;
import com.ticketflow.api.notification.EventNotificationPort;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * [SRP] Responsabilidade ÚNICA: EXECUTAR MUDANÇAS DE ESTADO em eventos.
 *
 * Compare com a versão anterior e note o que DESAPARECEU:
 *   - nenhum if de transição de status  -> foi para Event/EventStatus
 *   - nenhuma checagem de unicidade     -> foi para EventUniquenessChecker
 *   - nenhuma consulta                  -> foi para EventQueryService
 *
 * O que sobrou é a essência de um service: ORQUESTRAÇÃO.
 * Cada método agora se lê como uma receita de 3-4 passos.
 *
 * [SPRING TX] @Transactional (escrita) na classe. Como TODO método aqui
 * escreve, não há risco de esquecer. Isso resolve estruturalmente a pegadinha
 * que você descobriu no desafio 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EventCommandService {

    // [DIP] Depende de ABSTRAÇÕES/colaboradores, não de detalhes de implementação.
    private final EventRepository eventRepository;
    private final EventQueryService eventQueryService;
    private final EventUniquenessChecker uniquenessChecker;
    private final EventMapper eventMapper;
    private final EventNotificationPort notificationPort;
    private final EventCancellationPolicy eventCancellationPolicy;

    public EventResponse create(CreateEventRequest request){
        log.info("Creating event: {}", request.name());

        uniquenessChecker.assertNameIsAvailable(request.name());   // 1. valida
        Event event = eventMapper.toEntity(request);               // 2. converte
        Event saved = eventRepository.save(event);                 // 3. persiste

        log.info("Event create id={}", saved.getId());
        return eventMapper.toResponse(saved);                      // 4. responde
    }

    public EventResponse update(Long id, UpdateEventRequest request){
        Event event = eventQueryService.getRequiredEvent(id);

        // [CLEAN CODE] Compare com o antes:
        //   if (status == CANCELLED || status == FINISHED) throw ...
        // A regra "quais status são editáveis" vive AGORA no EventStatus.
        // Se amanhã SOLD_OUT virar editável, muda em 1 lugar.
        if(!event.getStatus().isEditable()){
            throw new BusinessRuleException(
                    "Cannot update an event with status " + event.getStatus(), "EVENT_NOT_EDITABLE");
        }

        uniquenessChecker.assertNameIsAvailableForUpdate(request.name(), id);
        eventMapper.updateEntity(event, request);   // dirty checking persiste
        return eventMapper.toResponse(event);
    }

    /**
     * [CLEAN CODE] Antes: 12 linhas com 2 ifs e um setter.
     * Agora: 3 linhas, zero if. A regra não desapareceu — MUDOU DE LUGAR,
     * para dentro do objeto que é dono dela. Isso é encapsulamento real.
     */
    public EventResponse publish(Long id){
        Event event = eventQueryService.getRequiredEvent(id);
        event.publish();
        notificationPort.notifyEventPublished(event);
        return eventMapper.toResponse(event);
    }

    public EventResponse cancel(Long id, String reason){
        Event event = eventQueryService.getRequiredEvent(id);
        eventCancellationPolicy.check(event, reason, Instant.now());
        //event.cancel();
        notificationPort.notifyEventCancelled(event, "TODO");
        // [FASE 9] Aqui publicaremos o domain event "EventCancelled" no Kafka.
        return eventMapper.toResponse(event);
    }

    public void delete(Long id){
        Event event = eventQueryService.getRequiredEvent(id);

        if(event.hasSales()){   // nome revela a intenção
            throw new BusinessRuleException(
                    "Cannot delete an event with sold tickets. Cancel it instead.", "EVENT_HAS_SALES");
        }
        eventRepository.delete(event);
        log.info("Event id={} deleted", id);
    }
}
