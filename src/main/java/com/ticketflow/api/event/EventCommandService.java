package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.CreateEventRequest;
import com.ticketflow.api.event.dto.EventResponse;
import com.ticketflow.api.event.dto.RescheduleEventRequest;
import com.ticketflow.api.event.dto.UpdateEventRequest;
import com.ticketflow.api.event.port.EventNotificationPort;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

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
    private final EventReschedulePolicy reschedulePolicy;
    private final EventUpdatePolicy updatePolicy;
    private final Clock clock;

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

        updatePolicy.ensureCanBeUpdated(event, request);
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
        event.publish(clock.instant());
        notificationPort.notifyEventPublished(event);
        return eventMapper.toResponse(event);
    }

    public EventResponse cancel(Long id, String reason){
        Event event = eventQueryService.getRequiredEvent(id);

        // 1. A POLICY decide se pode (pode lançar exceção -> handler devolve 409)
        eventCancellationPolicy.ensureCanBeCancelled(event, reason);

        // 2. A ENTIDADE executa a mudança de estado.
        //    ⚠️ ESTA LINHA É O CANCELAMENTO. Sem ela, nada acontece no banco.
        //    Não há save(): a entidade é MANAGED e o dirty checking dispara o
        //    UPDATE no commit da transação. (Conceito da Fase 2, em ação.)
        event.cancel();

        log.info("Event id={} cancelled. reason={}", id, reason);

        // 3. Notifica, passando o motivo REAL
        notificationPort.notifyEventCancelled(event, reason);

        // [FASE 9] Isto virará: eventPublisher.publishEvent(new EventCancelled(id, reason))
        // com @TransactionalEventListener(AFTER_COMMIT), para que a notificação
        // não rode dentro da transação nem possa causar rollback do cancelamento.
        
        return eventMapper.toResponse(event);
    }

    public EventResponse reschedule(Long id, RescheduleEventRequest request){
        Event event = eventQueryService.getRequiredEvent(id);
        EventStatus statusBefore = event.getStatus();

        reschedulePolicy.reschedule(event, request.newStartsAt(), request.newEndsAt());

        // Só notifica se já estava público — quem comprou precisa saber.
        if(statusBefore == EventStatus.PUBLISHED){
            notificationPort.notifyEventRescheduled(event);
        }
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
