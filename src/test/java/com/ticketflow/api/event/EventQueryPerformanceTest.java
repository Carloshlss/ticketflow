package com.ticketflow.api.event;

import com.ticketflow.api.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [PERFORMANCE COMO TESTE] Este é um padrão pouco usado e extremamente
 * valioso: transformar "quantas queries esta operação faz?" em ASSERÇÃO.
 *
 * Sem isto, o N+1 entra silenciosamente numa refatoração futura (basta alguém
 * trocar um fetch join por um acesso lazy) e só aparece em produção, quando a
 * tabela tem 500 mil linhas.
 *
 * Requer: spring.jpa.properties.hibernate.generate_statistics=true
 */
@DisplayName("Query count guard")
public class EventQueryPerformanceTest extends AbstractIntegrationTest {
    @Autowired private EventRepository eventRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp(){
        // [JPA -> HIBERNATE] unwrap() desce da abstração JPA para a API nativa
        // do Hibernate. Exemplo concreto de "JPA é a spec, Hibernate é o
        // fornecedor" — e de que às vezes você PRECISA do fornecedor.
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        assertThat(statistics.isStatisticsEnabled())
                .as("hibernate.generate_statics must be true in src/test/resources/application.yml");

        statistics.clear();
    }

    @Test
    @DisplayName("search should execute at most 2 queries (data + count")
    void givenPagedSearch_whenExecuted_thenQueryCountStaysWishinBudget(){
        eventRepository.findAll(
                EventSpecification.hasCity("Sao Paulo"),
                PageRequest.of(0,1));   // size=1 força a query de COUNT

        long queryCount = statistics.getPrepareStatementCount();

        // 🎯 ORÇAMENTO DE QUERIES. Quando Event ganhar List<Ticket> na Fase 8,
        // este assert vai FALHAR com ~21 queries — e você terá detectado o
        // N+1 automaticamente, em segundos, sem ler log nenhum.
        assertThat(queryCount)
                .as("paged search should not trigger N+1")
                .isLessThanOrEqualTo(2);
    }
}
