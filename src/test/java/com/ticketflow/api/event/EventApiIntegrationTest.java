package com.ticketflow.api.event;

import com.ticketflow.api.AbstractIntegrationTest;
import com.ticketflow.api.event.dto.EventResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [TESTE DE INTEGRAÇÃO COMPLETO] Aplicação inteira + Tomcat em porta real +
 * Postgres real. HTTP de verdade sobre a rede local.
 *
 * Diferença de MockMvc: aqui existe serialização/desserialização real,
 * conexão TCP, e todos os filtros da cadeia rodam (inclusive seu
 * CorrelationIdFilter e, na Fase 7, o Spring Security).
 *
 * ⚠️ Note a AUSÊNCIA de @Transactional: queremos que o COMMIT aconteça de
 * verdade, porque a requisição HTTP roda em outra thread e não veria uma
 * transação de teste. O preço é que precisamos limpar os dados nós mesmos.
 */
@DisplayName("Event API end-to-end")
public class EventApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired private RestTestClient restTestClient;
    @Autowired private EventRepository eventRepository;

    /**
     * ⚠️ DATA RELATIVA, não absoluta.
     *
     * "2027-03-15" viraria passado em algum momento e o teste falharia
     * misteriosamente no futuro — uma bomba de tempo. Aqui os testes de
     * integração usam o Clock REAL (não congelado), então o dado precisa
     * ser sempre futuro em relação ao agora de verdade.
     */
    private static final Instant FUTURE_START =
            Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    @AfterEach
    void cleanDatabase() {
        eventRepository.deleteAll();
    }

    @Test
    @DisplayName("full lifecycle: create -> publish -> cancel")
    void givenNewEvent_whenGoingThroughLifecycle_thenStatusTransitionsCorrectly(){
        // ═══ CREATE ═══
        String createJson = """
                {
                    "name": "Integration Test Festival",
                    "description": "Full lifecycle test",
                    "venue": "Test Arena",
                    "city": "Sao Paulo",
                    "organizerName": "Test Organizer",
                    "startsAt": "%s",
                    "endsAt": "%s",
                    "ticketPrice": 250.00,
                    "totalTickets": 500
                }
                """.formatted(FUTURE_START, FUTURE_START.plus(6, ChronoUnit.HOURS));

        EventResponse created = restTestClient.post()
                        .uri("/api/v1/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                        .body(createJson)
                                                .exchange()
                                                        .expectStatus().isCreated()
                        // [REST] 201 DEVE trazer Location. Exigido pela RFC 9110 e
                        // esquecido em 90% das APIs.
                        .expectHeader().exists("Location")
                        .expectBody(EventResponse.class)
                                .returnResult()
                                        .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo(EventStatus.DRAFT);
        Long id = created.id();

        // ═══ PUBLISH ═══
        restTestClient.post()
                .uri("/api/v1/events/{id}/publish", id)
                        .exchange()
                                .expectStatus().isOk()
                        // [JSONPATH] Asserção direta no corpo, sem desserializar.
                        .expectBody()
                                .jsonPath("$.status").isEqualTo("PUBLISHED");

        // 🎯 Confirmar NO BANCO, não só na resposta HTTP.
        // Isto prova que o dirty checking gerou o UPDATE e que houve COMMIT.
        // Note que este teste NÃO tem @Transactional — justamente para que o
        // commit aconteça de verdade e a leitura abaixo veja dado persistido.
        assertThat(eventRepository.findById(id))
                .get()
                .extracting(Event::getStatus)
                .isEqualTo(EventStatus.PUBLISHED);

        // ═══ PUBLISH AGAIN -> 409 ═══
        restTestClient.post()
                .uri("/api/v1/events/{id}/publish", id)
                        .exchange()
                                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                        .expectBody()
                                .jsonPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION");

        // ═══ CANCEL ═══
        restTestClient.post()
                .uri("/api/v1/events/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"reason\": \"Integration test cleanup\"}")
                                        .exchange()
                                                .expectStatus().isOk()
                        .expectBody()
                                .jsonPath("$.status").isEqualTo("CANCELLED");

        assertThat(eventRepository.findById(id))
                .get()
                .extracting(Event::getStatus)
                .isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    @DisplayName("should return 404 with ApiError body for unknown route")
    void givenNonExistentRout_whenCalling_thenReturns404NotFount(){
        // O teste que você consertou na Fase 4 — agora com HTTP real, provando
        // que o GlobalExceptionHandler não converte 404 em 500.
        restTestClient.get()
                .uri("/api/v1/nonexistent")
                        .exchange()
                                .expectStatus().isNotFound()
                        .expectBody()
                                .jsonPath("$.code").isEqualTo("ROUTE_NOT_FOUND")
                        .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("should return 400 with field errors for invalid payload")
    void givenInvalidPayload_whenCreating_thenReturns400WithFieldErrors(){
        String invalidJson = """
                {
                    "name": "",
                    "venue": "Venue",
                    "city": "Sao Paulo",
                    "startsAt": "2020-01-01T20:00:00Z",
                    "endsAt": "2020-01-01T23:00:00Z",
                    "ticketPrice": -20.00,
                    "totalTickets": 0
                }
                """;

        restTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalidJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.fieldErrors").isArray();
    }

    @Test
    @DisplayName("should clamp page size to the configured maximum")
    void givenExcessivePageSize_whenSearching_thenSizeIsClamped(){
        // Vetor de DoS real: ?size=5000 faria o Postgres materializar
        // milhares de linhas. Depende de spring.data.web.pageable.max-page-size.
        restTestClient.get()
                .uri("/api/v1/events/search?size=5000")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.size").isEqualTo(100);
    }
}
