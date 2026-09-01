package com.ticketflow.api.event;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * [PADRÃO SPECIFICATION] Cada método estático devolve um PEDAÇO de WHERE,
 * encapsulado num objeto combinável.
 *
 * [OCP] Filtro novo = método novo aqui. Nenhum método existente é tocado.
 *
 * [CLEAN CODE] final class + construtor privado: fábrica de Specifications,
 * não algo para instanciar.
 */
public final class EventSpecification {
    private EventSpecification(){}   // impede instanciação

    /**
     * [CRITERIA API] Anatomia de uma Specification. O lambda recebe 3 coisas:
     *   root  -> a entidade raiz da query (o "FROM Event e"). root.get("city")
     *            é o equivalente tipado de "e.city".
     *   query -> a query inteira; permite mexer em distinct, group by, order by
     *   cb    -> CriteriaBuilder: a "fábrica de operadores" (equal, like, gt...)
     *
     * O retorno é um Predicate = uma condição booleana do WHERE.
     *
     * O TRUQUE que faz o filtro opcional funcionar: se o valor é null,
     * devolvemos null. O Spring Data DESCARTA specifications nulas ao
     * combinar. Ou seja, "filtro não informado" simplesmente desaparece
     * do SQL — sem nenhum if na camada de cima.
     */
    public static Specification<Event> hasCity(String city){
        if(city == null || city.isBlank()){
            return (root, query, cb) -> null;
        }
            // cb.equal(cb.lower(x), y) -> WHERE lower(city) = ?
            return (root, query, cb) ->
                    cb.equal(cb.lower(root.get("city")), city.toLowerCase());
    }

    public static Specification<Event> hasStatus(EventStatus status){
        if(status == null) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Event> hasOrganizer(String organizerName){
        if(organizerName == null || organizerName.isBlank()){
            return (root, query, cb) -> null;
        }
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("organizerName")), organizerName.toLowerCase());
    }

    /**
     * Faixa de preço com os DOIS limites opcionais e independentes.
     * Isso é o que ficaria horrível com query methods.
     */
    public static Specification<Event> priceBetween(BigDecimal min, BigDecimal max){
        if(min == null && max == null){
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> {
            if (min == null) return cb.lessThanOrEqualTo(root.get("TicketPrice"), max);
            if (max == null) return cb.greaterThanOrEqualTo(root.get("ticketPrice"), max);
            return cb.between(root.get("ticketPrice"), min, max);
        };
    }

    public static Specification<Event> startsBetween(Instant from, Instant to){
        if(from == null && to == null){
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> {
            if (from == null) return cb.lessThanOrEqualTo(root.get("startsAt"), to);
            if (to == null) return cb.greaterThanOrEqualTo(root.get("startsAt"), from);
            return cb.between(root.get("startsAt"), from, to);
        };
    }

    /**
     * Busca textual em VÁRIOS campos com OR.
     * cb.or(...) combina predicados; cb.like com % faz a busca parcial.
     *
     * ⚠️ PERFORMANCE: LIKE '%texto%' com curinga NO INÍCIO não usa índice
     * B-tree — o Postgres faz Seq Scan. Aceitável em tabela pequena, veneno
     * em tabela grande. A solução real é FULL TEXT SEARCH (tsvector + índice
     * GIN) ou um motor dedicado (Elasticsearch). Fase 10.
     */
    public static Specification<Event> textSearch(String text){
        if(text == null || text.isBlank()){
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> {
            String pattern = "%" + text.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("venue")), pattern),
                    cb.like(cb.lower(root.get("organizerName")), pattern)
            );
        };
    }

    /** Specification sem parâmetro: uma regra fixa e nomeada. */
    public static Specification<Event> hasAvailableTickets() {
        return (root, query, cb) ->
                cb.greaterThan(root.get("availableTickets"), 0);
    }

    public static Specification<Event> isVisibleToPublic(){
        return (root, query, cb) ->
                root.get("status").in(EventStatus.PUBLISHED, EventStatus.SOLD_OUT);
    }

    public static Specification<Event> startsInTheFuture(){
        return (root, query, cb) ->
                cb.greaterThan(root.get("startsAt"), Instant.now());
    }
}
