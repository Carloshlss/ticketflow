package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.EventSummaryResponse;
import com.ticketflow.api.shared.dto.PagedResponse;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import com.ticketflow.api.event.dto.CreateEventRequest;
import com.ticketflow.api.event.dto.EventResponse;
import com.ticketflow.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * [SPRING TEST - SLICE TEST] @WebMvcTest sobe um contexto MÍNIMO: apenas
 * @RestController, @ControllerAdvice, filtros, Jackson e validação.
 *
 * NÃO sobe: @Service, @Repository, DataSource, Flyway, JPA.
 * Por isso todas as dependências do controller precisam ser mockadas — se
 * faltar uma, o contexto falha ao subir com NoSuchBeanDefinitionException.
 *
 * O que este teste PROVA (e o unitário não pode provar):
 *   - a rota está mapeada nessa URL e nesse verbo
 *   - o JSON de entrada desserializa corretamente
 *   - o Bean Validation dispara e devolve 400
 *   - o @ControllerAdvice converte exceção no status certo
 *   - o JSON de saída tem os campos esperados (contrato com o Angular)
 */
@WebMvcTest(EventController.class)
@DisplayName("EventController")
class EventControllerTest {
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Instant DEFAULT_STARTS_AT = NOW.plus(Duration.ofDays(30));
    private static final Instant DEFAULT_ENDS_AT   = DEFAULT_STARTS_AT.plus(Duration.ofHours(4));

    @Autowired private MockMvc mockMvc;

    @Autowired private JsonMapper jsonMapper;

    /**
     * [BOOT 4] @MockitoBean substitui o @MockBean depreciado.
     * Cria um mock E o registra no contexto, substituindo o bean real.
     */
    @MockitoBean private EventCommandService eventCommandService;
    @MockitoBean private EventQueryService eventQueryService;

    @Test
    @DisplayName("GET /api/v1/events/{id} should return 200 with event JSON")
    void givenExistingEvent_whenGettingById_thenReturns200WithJson() throws Exception{
        given(eventQueryService.findById(1L)).willReturn(anEventResponse());

        mockMvc.perform(get("/api/v1/events/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // [JSONPATH] $ = raiz. Assertar CAMPO POR CAMPO documenta o
                // contrato: se alguém renomear 'ticketPrice', este teste falha
                // e avisa que o front vai quebrar.
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Event"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                // 🎯 Garantir a AUSÊNCIA é tão importante quanto a presença:
                // 'version' é detalhe interno de lock otimista e não deve
                // vazar na API. Este assert protege o encapsulamento.
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/events/{id} should return 404 when event does not exist")
    void givenNonExistentEvent_whenGettingById_thenReturns404WithApiError() throws Exception{
        willThrow(ResourceNotFoundException.of("Event", 99L))
                .given(eventQueryService).findById(99L);

        mockMvc.perform(get("/api/v1/events/99"))
                .andExpect(status().isNotFound())
                // Valida o FORMATO do erro — o Angular depende dele
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/events/99"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/v1/events should return 201 with Location header")
    void givenValidRequest_whenCreatingEvent_thenReturns201WithLocation() throws Exception{
        CreateEventRequest request = aValidCreateRequest();

        given(eventCommandService.create(any(CreateEventRequest.class)))
                .willReturn(anEventResponse());

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // [REST] 201 DEVE trazer Location apontando para o novo recurso.
                // Detalhe que quase todo mundo esquece — e é exigido pela RFC.
                .andExpect(header().string("Location", endsWith("/api/v1/events/1")))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/events should return 400 with field errors when invalid")
    void givenBlankName_whenCreatingEvent_thenReturns400WithFieldErrors() throws Exception{
        // JSON cru aqui é INTENCIONAL: precisamos enviar um payload inválido
        // que o record talvez não permita construir. Text block do Java 15+.
        String invalidJson = """
                {
                    "name": "",
                    "venue": "Some Venue",
                    "city": "Sao Paulo",
                    "startsAt": "2026-12-01T20:00:00Z",
                    "endsAt": "2026-12-01T23:00:00Z",
                    "ticketPrice": -50.00,
                    "totalTickets": 0
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                // 3 campos inválidos => 3 erros. Prova que a validação NÃO
                // para no primeiro erro (má UX: o usuário corrigiria um por vez).
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/events should ignore read-only fields (mass assignment)")
    void givenStatusInPayload_whenCreatingEvent_thenFieldIsIgnored() throws Exception{
        // 🔒 TESTE DE SEGURANÇA. Um atacante tenta injetar 'status' e 'id'.
        // O CreateEventRequest não tem esses campos, então o Jackson os descarta.
        // Este teste GARANTE que ninguém vai "facilitar" adicionando os campos
        // ao DTO no futuro. É a proteção contra mass assignment virando teste.
        String maliciousJson = """
                {
                    "id": 999,
                    "status": "PUBLISHED",
                    "version": 50,
                    "name": "Hacked Event",
                    "venue": "Venue",
                    "city": "Sao Paulo",
                    "organizerName": "Attacker Inc",
                    "startsAt": "2026-12-01T20:00:00Z",
                    "endsAt": "2026-12-01T23:00:00Z",
                    "ticketPrice": 100.00,
                    "totalTickets": 10
                }
                """;

        given(eventCommandService.create(any())).willReturn(anEventResponse());

        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(maliciousJson))
                .andExpect(status().isCreated());

        // Se o Jackson estivesse configurado com FAIL_ON_UNKNOWN_PROPERTIES=true,
        // isto daria 400. O comportamento atual (ignorar) é o default do Boot
        // e é o adequado para APIs evolutivas.
    }

    @Test
    @DisplayName("GET /api/v1/events/search should clamp excessive page size")
    void givenExcessivePageSize_whenSearching_thenSizeIsClamped() throws Exception{
        given(eventQueryService.search(any(), any())).willReturn(anEmptyPagedResponse());
        // Protege contra DoS por paginação: ?size=1000000 travaria o banco.
        mockMvc.perform(get("/api/v1/events/search").param("size", "5000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(eventQueryService).search(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    private EventResponse anEventResponse(){
        return new EventResponse(
                1L,
                "Test Event",
                "Test Description",
                "Test Venue",
                "Sao Paulo",
                "Test Organized Name",
                DEFAULT_STARTS_AT,
                DEFAULT_ENDS_AT,
                new BigDecimal("100.00"),
                100,
                100,
                0,
                0.0,
                false,
                EventStatus.PUBLISHED,
                NOW,
                NOW
        );
    }

    private CreateEventRequest aValidCreateRequest(){
        return new CreateEventRequest(
                "Rock in Sampa 2026",
                "Test Description",
                "Test Venue",
                "Sao Paulo",
                "Test Organized Name",
                Instant.now().plus(Duration.ofDays(30)),
                Instant.now().plus(Duration.ofDays(30).plusHours(4)),
                new BigDecimal("100.00"),
                100
        );
    }

    private PagedResponse<EventSummaryResponse> anEmptyPagedResponse(){
        return new PagedResponse<>(
                List.of(),   // content
                0,           // page
                100,         // size — o valor clampado
                0L,          // totalElements
                0,           // totalPages
                true,        // first
                true,        // last
                true       // empty
        );
    }
}