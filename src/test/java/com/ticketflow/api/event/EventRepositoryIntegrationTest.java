package com.ticketflow.api.event;

import com.ticketflow.api.AbstractIntegrationTest;
import com.ticketflow.api.event.dto.CityEventCount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * [TESTE DE INTEGRAÇÃO] Postgres real, Flyway real, Hibernate real, SQL real.
 *
 * O que este teste PROVA e nenhum unitário pode provar:
 *   - as Specifications geram SQL VÁLIDO (pega o bug do "TicketPrice"!)
 *   - as migrations do Flyway rodam sem erro
 *   - as CHECK constraints do banco funcionam
 *   - o mapeamento entidade↔tabela está correto (ddl-auto: validate)
 *   - Instant ↔ TIMESTAMPTZ converte certo
 *
 * @Transactional em teste tem semântica ESPECIAL: o Spring faz ROLLBACK
 * automático no fim de cada método. Cada teste vê um banco limpo, sem
 * precisar de DELETE manual. Isolamento de graça.
 *
 * ⚠️ Mas atenção à armadilha: com @Transactional, o teste roda na MESMA
 * transação do código testado. Isso significa que (a) você nunca prova que
 * o commit funcionaria, e (b) o Persistence Context é compartilhado, então
 * uma entidade pode parecer "salva" por estar apenas em cache. Para testar
 * commit de verdade, omita @Transactional e limpe você mesmo.
 */
@Transactional
@DisplayName("EventRepository integration")
public class EventRepositoryIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private int sequence = 0;

    @Autowired private EventRepository eventRepository;

    @Autowired private EntityManager entityManager;

    @BeforeEach
    void setUp(){
        // As migrations V2 já inseriram seeds. Limpamos para ter controle total
        // do cenário — teste não deve depender de dado de seed, que muda.
        eventRepository.deleteAll();
        eventRepository.flush();
    }

    @Test
    @DisplayName("priceBetween should filter with only max price informed")
    void givenOnlyMaxPrice_whenFilteringByPrice_thenReturnsCheaperEvents(){
        // 🎯 ESTE É O TESTE QUE PEGA O BUG DO "TicketPrice" COM T MAIÚSCULO.
        // Nenhum teste unitário pegaria: a Criteria API só resolve o nome do
        // atributo quando gera o SQL, contra o metamodelo do Hibernate.
        eventRepository.saveAll(java.util.List.of(
                anEvent("Cheap Event", "Sao Paulo", new BigDecimal("50.00")),
                anEvent("Pricey Event", "Sao Paulo", new BigDecimal("500.00"))
        ));
        eventRepository.flush();

        Page<Event> result = eventRepository.findAll(
                EventSpecification.priceBetween(null, new BigDecimal("100.00")),
                PageRequest.of(0, 10));

                assertThat(result.getContent())
                        .hasSize(1)
                        .extracting(Event::getName)
                        .containsExactly("Cheap Event");
    }

    @Test
    @DisplayName("priceBetween should filter with only min price informed")
    void givenOnlyMinPrice_whenFilteringByPrice_thenReturnsExpensiveEvents(){
        // E este pega o bug do 'max' no lugar do 'min'.
        eventRepository.saveAll(java.util.List.of(
                anEvent("Cheap Event", "Sao Paulo", new BigDecimal("50.00")),
                anEvent("Pricey Event", "Sao Paulo", new BigDecimal("500.00"))
        ));
        eventRepository.flush();

        Page<Event> result = eventRepository.findAll(
                EventSpecification.priceBetween(new BigDecimal("100.00"), null),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Event::getName)
                .containsExactly("Pricey Event");
    }

    @Test
    @DisplayName("hasOrganized should perform partial case-insensitive match")
    void givenPartialOrganizerName_whenFiltering_thenMatchesSubstring(){
        // Pega o bug do LIKE sem % (busca exata em vez de parcial)
        Event event = anEvent("Some Event", "Sao Paulo", new BigDecimal("100.00"));
        event.setOrganizerName("King Events Productions");
        eventRepository.saveAndFlush(event);

        Page<Event> result = eventRepository.findAll(
            EventSpecification.hasOrganizer("king"),   // minúsculo e parcial
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("null specification should behave as no filter at all")
    void givenAllFiltersNull_whenSearching_thenReturnsEverything(){
        eventRepository.saveAll(java.util.List.of(
                anEvent("A", "Sao Paulo", new BigDecimal("10.00")),
                anEvent("B", "Rio de Janeiro", new BigDecimal("20.00"))
        ));
        eventRepository.flush();

        // Todas as specs devolvem null => WHERE não é gerado
        Specification<Event> spec = Specification.allOf(
                EventSpecification.hasCity(null),
                EventSpecification.hasStatus(null),
                EventSpecification.priceBetween(null, null),
                EventSpecification.textSearch("  "),
                EventSpecification.hasOrganizer(null)
        );

        assertThat(eventRepository.findAll(
                spec, PageRequest.of(0, 10)).getContent()).hasSize(2);
    }

    @Test
    @DisplayName("database CHECK constraint should reject available > total tickets")
    void givenAvailableGreaterThanTotal_whenSaving_thenDatabaseRejects(){
        // 🎯 Prova que a integridade existe NO BANCO, não só na aplicação.
        // Um script SQL direto, ou outra instância da app com bug, não furam isto.
        Event event = anEvent("Broken", "Sao Paulo", new BigDecimal("100.00"));
        event.setTotalTickets(10);
        event.setAvailableTickets(50);   // viola chk_event_tickets

        assertThatThrownBy(() -> eventRepository.saveAndFlush(event)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Instant should round-trip through TIMESTAMPTZ with drift")
    void givenInstantWithMicroseconds_whenPersistedAndRead_thenValueIsPreserved(){
        // Postgres TIMESTAMPTZ tem precisão de MICROssegundos; Instant tem
        // NANOssegundos. Truncar antes de comparar evita um teste "flaky" que
        // falha só quando o nanossegundo não é zero.
        Instant startsAt = Instant.parse("2026-12-01T20:30:00Z").truncatedTo(ChronoUnit.MICROS);

        Event event = anEvent("Precise", "Sao Paulo", new BigDecimal("100.00"));
        event.setStartsAt(startsAt);
        event.setEndsAt(startsAt.plus(3, ChronoUnit.HOURS));

        Long id = eventRepository.saveAndFlush(event).getId();

        // ⚠️ SEM O clear(), o findById devolveria a MESMA instância do cache
        // de primeiro nível, e o teste passaria SEM tocar o banco — um falso
        // positivo. O clear força um SELECT real.
        eventRepository.flush();
        entityManager.clear();

        assertThat(eventRepository.findById(id))
                .get()
                .extracting(Event::getStartsAt)
                .isEqualTo(startsAt);
    }

    @Test
    @DisplayName("should group published events by city, ordered by count")
    void givenEventsInMultipleCities_whenCounting_thenReturnsGroupedProjection(){
        eventRepository.saveAll(List.of(
                publishedEventIn("Sao Paulo"),
                publishedEventIn("Sao Paulo"),
                publishedEventIn("Rio de Janeiro"),
                draftEventIn("Curitiba")
        ));
        eventRepository.flush();

        List<CityEventCount> result = eventRepository.countPublishedEventsByCity();

        // [ASSERTJ] extracting + tuple: asserta múltiplos campos de cada item
        // em uma linha. containsExactly valida também a ORDEM — que aqui é
        // parte do contrato (ORDER BY total DESC).
        assertThat(result)
                .extracting(CityEventCount::getCity, CityEventCount::getTotal)
                .containsExactly(
                        tuple("Sao Paulo", 2L),
                        tuple("Rio de Janeiro", 1L)
                );

        // 🎯 Assert explícito da AUSÊNCIA: prova que o WHERE status='PUBLISHED'
        // existe. Sem ele, remover o WHERE não quebraria nenhum teste.
        assertThat(result)
                .extracting(CityEventCount::getCity)
                .doesNotContain("Curitiba");
    }

    private Event publishedEventIn(String city){
        Event event = anEvent("Event %d in %s".formatted(++sequence, city), city, new BigDecimal("100.00"));
        event.setStatus(EventStatus.PUBLISHED);
        return event;
    }

    private Event draftEventIn(String city){
        Event event = anEvent("Draft in " + city, city, new BigDecimal("100.00"));
        event.setStatus(EventStatus.DRAFT);
        return event;
    }

    private Event anEvent(String name, String city, BigDecimal ticketPrice){
        Instant startsAt = NOW.plus(Duration.ofDays(30));
        Event event = new Event();
        event.setName(name);
        event.setDescription("Description of " + name);
        event.setVenue("Test Venue");
        event.setCity(city);
        event.setOrganizerName("Test Organizer Name");
        event.setStartsAt(startsAt);
        event.setEndsAt(startsAt.plus(Duration.ofHours(4)));
        event.setTicketPrice(ticketPrice);
        event.setTotalTickets(100);
        event.setAvailableTickets(100);
        event.setStatus(EventStatus.PUBLISHED);
        return event;
    }
}
