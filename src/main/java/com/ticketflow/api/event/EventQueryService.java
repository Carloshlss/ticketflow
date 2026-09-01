package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.EventFilter;
import com.ticketflow.api.event.dto.EventResponse;
import com.ticketflow.api.event.dto.EventSummaryResponse;
import com.ticketflow.api.shared.dto.PagedResponse;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import com.ticketflow.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [SRP] Responsabilidade ÚNICA: RESPONDER PERGUNTAS sobre eventos.
 * Motivo único para mudar: mudou a forma de consultar/apresentar eventos.
 *
 * [SPRING TX] readOnly=true na classe e NENHUM método a sobrescreve.
 * Isso é uma garantia estrutural: esta classe é fisicamente incapaz de
 * gravar no banco. Compare com a versão anterior, onde o readOnly era
 * "um default que às vezes é sobrescrito" — muito mais fácil de errar.
 *
 * [FASE 8] Esta classe é o alvo natural do @Cacheable com Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventQueryService {
    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    public PagedResponse<EventSummaryResponse> findAll(Pageable pageable){
        return toPagedSummary(eventRepository.findAll(pageable));
    }

    public EventResponse findById(Long id){
        return eventMapper.toResponse(getRequiredEvent(id));
    }

    public PagedResponse<EventSummaryResponse> findPublishedByCity(String city, Pageable pageable){
        return toPagedSummary(eventRepository.findByCityIgnoreCaseAndStatusOrderByStartsAtAsc(
                city, EventStatus.PUBLISHED, pageable));
    }

    /**
     * [CLEAN CODE - DRY] O "buscar ou 404" agora é PUBLIC e reutilizado
     * pelo EventCommandService. Evita duplicar a lógica de not-found.
     *
     * [CLEAN CODE] Nome: getRequiredEvent, não findEventById.
     * Convenção que vale adotar: 'find' pode não achar (retorna Optional);
     * 'get'/'getRequired' garante que acha ou explode. A assinatura conta a história.
     */
    public Event getRequiredEvent(Long id){
        return eventRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Event", id));
    }

    /** [CLEAN CODE] Elimina a repetição do PagedResponse.from em 3 métodos. */
    private PagedResponse<EventSummaryResponse> toPagedSummary(Page<Event> page){
        return PagedResponse.from(page, eventMapper::toSummaryResponse);
    }

    /**
     * [SPECIFICATION] Busca com filtros dinâmicos e combináveis.
     *
     * Observe: ZERO if. Cada Specification decide sozinha se se aplica
     * (retornando null quando o parâmetro está ausente), e o Spring Data
     * ignora as nulas ao compor o WHERE.
     *
     * [OCP] Adicionar um filtro = adicionar uma linha aqui + um método na
     * fábrica. Nenhum código existente muda.
     *
     * [JAVA 8] Specification.allOf(...) combina com AND.
     * (Em versões anteriores era Specification.where(a).and(b).and(c) —
     *  você verá isso em tutoriais antigos; allOf é mais limpo.)
     */
    public PagedResponse<EventSummaryResponse> search(EventFilter filter, Pageable pageable){
        if (filter.hasInvalidPriceRange()) {
            throw new BusinessRuleException(
                    "minPrice cannot be greater than maxPrice", "INVALID_PRICE_RANGE");
        }
        Specification<Event> spec = Specification.allOf(
                EventSpecification.hasCity(filter.city()),
                EventSpecification.hasStatus(filter.status()),
                EventSpecification.hasOrganizer(filter.organizerName()),
                EventSpecification.priceBetween(filter.minPrice(), filter.maxPrice()),
                EventSpecification.startsBetween(filter.startsFrom(), filter.startsTo()),
                EventSpecification.textSearch(filter.search()),
                filter.isOnlyAvailable() ? EventSpecification.hasAvailableTickets()
                        : (root, query, cb) -> null
        );
        return toPagedSummary(eventRepository.findAll(spec, pageable));
    }

    /**
     * Especificação de negócio REUTILIZÁVEL, montada a partir de peças.
     * "Vitrine" = visível ao público + no futuro + com ingresso.
     *
     * [CLEAN CODE] O nome do método expressa o CONCEITO DE NEGÓCIO;
     * as três specifications são o detalhe. Se a definição de "vitrine"
     * mudar, muda aqui — e todo mundo que usa vitrine já está correto.
     */
    public PagedResponse<EventSummaryResponse> findShowcase(EventFilter filter, Pageable pageable){
        Specification<Event> spec = Specification.allOf(
                EventSpecification.isVisibleToPublic(),
                EventSpecification.startsInTheFuture(),
                EventSpecification.hasAvailableTickets(),
                EventSpecification.hasCity(filter.city()),
                EventSpecification.priceBetween(filter.minPrice(), filter.maxPrice()),
                EventSpecification.textSearch(filter.search())
        );
        return toPagedSummary(eventRepository.findAll(spec, pageable));
    }
}
