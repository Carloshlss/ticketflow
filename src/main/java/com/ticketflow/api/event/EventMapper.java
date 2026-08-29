package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.CreateEventRequest;
import com.ticketflow.api.event.dto.EventResponse;
import com.ticketflow.api.event.dto.EventSummaryResponse;
import com.ticketflow.api.event.dto.UpdateEventRequest;
import org.springframework.stereotype.Component;

/**
 * [SPRING CORE] @Component: bean genérico gerenciado pelo container.
 * Diferença entre os estereótipos — todos são @Component especializados:
 *   @Component  -> genérico
 *   @Service    -> regra de negócio (semântica)
 *   @Repository -> persistência (+ tradução de exceções JDBC)
 *   @Controller / @RestController -> camada web (+ handler mapping)
 * Funcionalmente @Component e @Service são idênticos; a diferença é
 * INTENÇÃO comunicada ao leitor. [CLEAN CODE]
 *
 * [SOLID - Single Responsibility] A conversão Entity <-> DTO é uma
 * responsabilidade própria. Deixá-la no service inflaria o service; deixá-la
 * no controller espalharia a regra por vários endpoints.
 *
 * Esta classe é STATELESS (sem campo mutável), logo é thread-safe e pode ser
 * singleton — o escopo padrão do Spring. Beans com estado mutável em escopo
 * singleton são uma das piores fontes de bug em aplicação concorrente.
 */
@Component
public class EventMapper {

    /**
     * DTO de criação -> Entidade nova.
     * Note quem define os campos que o cliente NÃO controla: aqui, o servidor.
     */
    public Event toEntity(CreateEventRequest request){
        return Event.builder()
                .name(request.name())              // [RECORD] acesso sem "get"
                .description(request.description())
                .venue(request.venue())
                .city(request.city())
                .organizerName(request.organizerName())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .ticketPrice(request.ticketPrice())
                .totalTickets(request.totalTickets())
                // REGRA: um evento novo nasce com todos os ingressos disponíveis
                .availableTickets(request.totalTickets())
                // REGRA: nasce como DRAFT, nunca já publicado
                .status(EventStatus.DRAFT)
                .build();
        // id, createdAt, updatedAt e version: Hibernate + auditoria preenchem
    }

    /**
     * Aplica um update numa entidade EXISTENTE.
     *
     * ⚠️ Ponto crucial: NÃO criamos uma entidade nova. Alteramos a instância
     * MANAGED recebida. É o dirty checking do Hibernate que vai transformar
     * isso num UPDATE no commit — sem precisar chamar save().
     *
     * void em vez de retornar Event: a assinatura já comunica que o efeito
     * é a mutação do argumento. [CLEAN CODE]
     */
    public void updateEntity(Event event, UpdateEventRequest request){
        event.setName(request.name());
        event.setDescription(request.description());
        event.setVenue(request.venue());
        event.setCity(request.city());
        event.setOrganizerName(request.organizerName());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setTicketPrice(request.ticketPrice());
        // totalTickets, availableTickets e status: NÃO se alteram por aqui
    }

    /** Entidade -> DTO completo, com os campos derivados calculados. */
    public EventResponse toResponse(Event event){
        int sold = event.getTotalTickets() - event.getAvailableTickets();


        // Cast para double antes da divisão: int/int em Java trunca!
        // 5/10 == 0. Erro clássico e silencioso.
        double occupancy = event.getTotalTickets() == 0 ? 0.0 : (double) sold / event.getTotalTickets() * 100;

        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getVenue(),
                event.getCity(),
                event.getOrganizerName(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getTicketPrice(),
                event.getTotalTickets(),
                event.getAvailableTickets(),
                sold,
                Math.round(occupancy*100) / 100.0,
                event.getAvailableTickets() == 0,
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    /** Entidade -> DTO resumido, para listagens. */
    public EventSummaryResponse toSummaryResponse(Event event){
        return new EventSummaryResponse(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getCity(),
                event.getStartsAt(),
                event.getTicketPrice(),
                event.getAvailableTickets(),
                event.getAvailableTickets() == 0
        );
    }
}