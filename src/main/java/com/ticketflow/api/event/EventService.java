package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.*;
import com.ticketflow.api.shared.dto.PagedResponse;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import com.ticketflow.api.shared.exception.DuplicateResourceException;
import com.ticketflow.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [SPRING CORE] @Service: onde vive a REGRA DE NEGÓCIO e a orquestração.
 *
 * Contrato desta camada:
 *   ✅ decide, valida regra, coordena repositórios, controla transação
 *   ✅ fala DTO na fronteira e Entidade por dentro
 *   ❌ NÃO conhece HTTP (nada de ResponseEntity, status code, header)
 *   ❌ NÃO conhece SQL/JPQL (isso é do repositório)
 *
 * [SOLID - Dependency Inversion] O service depende da INTERFACE
 * EventRepository, não de uma implementação concreta. Em teste unitário
 * (Fase 5) trocamos por um mock sem tocar nesta classe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
// [SPRING TX] @Transactional na CLASSE = default para todos os métodos.
// readOnly=true como padrão SEGURO: quem escreve precisa declarar
// explicitamente. Inverte o risco a nosso favor.
@Transactional(readOnly = true)
public class EventService {
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    // ==================== LEITURA ====================

    /**
     * [API REST] Listagem paginada.
     * Herda readOnly=true da classe: sem dirty checking, sem flush.
     */
    public PagedResponse<EventSummaryResponse> findAll(Pageable pageable){
        log.debug("Fetching events page={} size={}", pageable.getPageNumber(), pageable.getPageSize());

        // Herdado de JpaRepository. Gera SELECT ... LIMIT ? OFFSET ?
        // + um SELECT COUNT(*) para os metadados do Page.
        return PagedResponse.from(
                eventRepository.findAll(pageable),
                eventMapper::toSummaryResponse   // [JAVA 8] method reference
        );
    }

    /** Busca por id. Optional força o tratamento do "não achei". */
    public EventResponse findById(Long id){
        Event event = findEntityById(id);
        return eventMapper.toResponse(event);
    }

    /** Filtro por cidade — usa o índice composto criado na migration V1. */
    public PagedResponse<EventSummaryResponse> findPublishedByCity(String city, Pageable pageable){
        return PagedResponse.from(
                eventRepository.findByCityIgnoreCaseAndStatusOrderByStartsAtAsc(city, EventStatus.PUBLISHED, pageable),
                eventMapper::toSummaryResponse
        );
    }

    /**
     * [CLEAN CODE] Método privado para eliminar a repetição de
     * "busca ou lança 404" espalhada por 6 métodos públicos.
     * Retorna a ENTIDADE (uso interno), não DTO.
     */
    private Event findEntityById(Long id){
        return eventRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Event", id));
        // Lambda no orElseThrow (não orElseThrow(new ...)): a exceção só é
        // CONSTRUÍDA se o Optional estiver vazio. Criar exceção é caro
        // (captura da stack trace). Detalhe de performance real.
    }

    // ==================== ESCRITA ====================

    /**
     * [SPRING TX] Sobrescreve o readOnly da classe. Este método ESCREVE.
     * Sem esta linha, o Hibernate não faria flush e o INSERT nunca sairia
     * (ou explodiria com "Connection is read-only").
     */
    @Transactional
    public EventResponse create(CreateEventRequest request){
        log.info("Creating event: {}", request.name());

        // REGRA DE NEGÓCIO 1: nome único
        // ⚠️ Isto é um check TOCTOU (time-of-check to time-of-use): entre o
        // exists e o insert, outra thread pode inserir o mesmo nome.
        // Garantia real = UNIQUE constraint no banco (desafio desta fase).
        // Este check existe para dar uma mensagem BONITA no caso comum.
        if(eventRepository.existsByNameIgnoreCase(request.name())){
            throw DuplicateResourceException.of("Event", "name", request.name());
        }

        Event event = eventMapper.toEntity(request);
        Event saved = eventRepository.save(event);   // sempre use o retorno

        log.info("Event created id={}", saved.getId());

        return eventMapper.toResponse(saved);
    }

    /**
     * [API REST] PUT = substituição. Note o que NÃO fazemos aqui:
     * nenhum save() explícito.
     *
     * Por quê? findEntityById devolve a entidade MANAGED (estamos dentro da
     * transação). O mapper a altera. No commit, o dirty checking do Hibernate
     * compara com o snapshot e dispara o UPDATE sozinho.
     *
     * Isto é o conceito da Fase 2 acontecendo de verdade. Chamar save() aqui
     * não estaria errado, apenas redundante.
     */
    @Transactional
    public EventResponse update(Long id, UpdateEventRequest request){
        log.info("Updating event id={}", id);

        Event event = findEntityById(id);

        // REGRA: evento CANCELLED ou FINISHED é imutável
        if(event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.FINISHED){
            throw new BusinessRuleException("Cannot update an event with status " + event.getStatus(), "EVENT_NOT_EDITABLE");
        }

        // REGRA: nome único (ignorando o próprio evento)
        eventRepository.findByNameIgnoreCase(request.name())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw DuplicateResourceException.of("Event", "name", request.name());
                });

        eventMapper.updateEntity(event, request);   // dirty checking cuida do resto
        return eventMapper.toResponse(event);
    }

    /**
     * [API REST] Transição de estado como SUB-RECURSO, não como campo.
     * POST /events/{id}/publish é melhor que PATCH {"status":"PUBLISHED"} porque:
     *   - a operação tem regras próprias (não é "setar um campo")
     *   - o nome expressa a INTENÇÃO
     *   - impede transições inválidas por construção
     * Isso é o embrião de um State Machine bem modelado.
     */
    @Transactional
    public EventResponse publish(Long id){
        Event event = findEntityById(id);

        if(event.getStatus() != EventStatus.DRAFT){
            throw new BusinessRuleException("Only DRAFT events can be published. Current status: " + event.getStatus(),
                    "INVALID_STATUS_TRANSITION");
        }
        if(event.getStartsAt().isBefore(java.time.Instant.now())){
            throw new BusinessRuleException("Cannot publish an event that already started", "EVENT_ALREADY_STARTED");
        }

        event.setStatus(EventStatus.PUBLISHED);
        log.info("Event id={} published", id);
        // Aqui publicaremos um evento Kafka "EventCancelled"
        // para disparar reembolsos e notificações de forma assíncrona.
        return eventMapper.toResponse(event);
    }

    /**
     * [API REST] DELETE.
     * REGRA: proibido apagar evento com venda. Dado com histórico financeiro
     * não se apaga — se cancela. Em sistema real, delete físico é exceção;
     * o padrão é soft delete (coluna deleted_at).
     */
    @Transactional
    public EventResponse cancel(Long id){
        Event event = findEntityById(id);

        if(event.getStatus() == EventStatus.FINISHED){
            throw new BusinessRuleException("Cannot cancel a finished event", "EVENT_ALREADY_FINISHED");
        }
        event.setStatus(EventStatus.CANCELLED);
        log.info("Event id={} cancelled", id);
        return eventMapper.toResponse(event);
    }

    @Transactional
    public void delete(Long id){
        Event event = findEntityById(id);

        if(event.getAvailableTickets() < event.getTotalTickets()){
            throw new BusinessRuleException("Canot delete an event with sold tickets. Cancel it instead.",
                    "EVENT_HAS_SALES");
        }
        eventRepository.delete(event);
        log.info("Event id={} deleted", id);
    }
}
