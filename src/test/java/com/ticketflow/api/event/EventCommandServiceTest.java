package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.CreateEventRequest;
import com.ticketflow.api.event.dto.EventResponse;
import com.ticketflow.api.event.dto.RescheduleEventRequest;
import com.ticketflow.api.event.dto.UpdateEventRequest;
import com.ticketflow.api.event.port.EventNotificationPort;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * [MOCKITO] @ExtendWith(MockitoExtension.class) é a integração Mockito↔JUnit 5.
 * Ela: (1) inicializa os @Mock antes de cada teste, (2) injeta em @InjectMocks,
 * (3) valida no fim que não há stubbing desnecessário (strict stubs).
 *
 * ⚠️ AINDA NÃO É SPRING. Nenhum contexto sobe. É o Mockito criando os objetos
 * e o construtor sendo chamado por reflection. Roda em milissegundos.
 *
 * @Mock       -> dublê: métodos devolvem null/0/false até você "ensinar"
 * @InjectMocks-> a classe REAL sob teste, com os mocks no construtor
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventCommandService")
class EventCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Instant DEFAULT_STARTS_AT = NOW.plus(Duration.ofDays(30));
    private static final Instant DEFAULT_ENDS_AT   = DEFAULT_STARTS_AT.plus(Duration.ofHours(4));

    @Mock private EventRepository eventRepository;
    @Mock private EventQueryService eventQueryService;
    @Mock private EventUniquenessChecker uniquenessChecker;
    @Mock private EventMapper eventMapper;
    @Mock private EventNotificationPort notificationPort;
    @Mock private EventCancellationPolicy cancellationPolicy;
    @Mock private EventReschedulePolicy reschedulePolicy;
    private final EventUpdatePolicy updatePolicy = new EventUpdatePolicy();

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private EventCommandService service;   // objeto REAL

    @BeforeEach
    void setUp(){
        service = new EventCommandService(eventRepository, eventQueryService, uniquenessChecker, eventMapper,
                notificationPort, cancellationPolicy, reschedulePolicy, updatePolicy, clock);
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("should create a event as DRAFT with all tickets available")
        void givenValidRequest_whenCreating_thenEventIsSavedAsDraft() {
            CreateEventRequest request = aCreateRequest("New Festival");

            // [MOCKITO] willAnswer devolve o próprio argumento recebido —
            // simula o save() do JPA, que retorna a entidade gerenciada.
            given(eventRepository.save(any(Event.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(eventMapper.toEntity(request)).willReturn(anEvent(EventStatus.DRAFT));
            given(eventMapper.toResponse(any(Event.class))).willReturn(anEventResponse(EventStatus.DRAFT));

            EventResponse result = service.create(request);

            assertThat(result.status()).isEqualTo(EventStatus.DRAFT);

            // [ARGUMENT CAPTOR] Inspeciona a entidade que REALMENTE foi salva.
            // É a única forma de assertar sobre um objeto criado dentro do
            // método sob teste.
            ArgumentCaptor<Event> savedEvent = ArgumentCaptor.forClass(Event.class);
            verify(eventRepository).save(savedEvent.capture());

            assertThat(savedEvent.getValue().getStatus()).isEqualTo(EventStatus.DRAFT);

            // 🎯 Regra de negócio: um evento novo nasce com 100% de ingressos
            // disponíveis. Se alguém "otimizar" e esquecer de inicializar,
            // este assert pega.
            assertThat(savedEvent.getValue().getAvailableTickets())
                    .isEqualTo(savedEvent.getValue().getTotalTickets());
        }

        @Test
        @DisplayName("should not notify anyone when creating an event")
        void givenValidRequest_whenCreating_thenNoNotificationIsSent() {
            CreateEventRequest request = aCreateRequest("New Festival");

            given(eventRepository.save(any(Event.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(eventMapper.toEntity(request)).willReturn(anEvent(EventStatus.DRAFT));
            given(eventMapper.toResponse(any(Event.class))).willReturn(anEventResponse(EventStatus.DRAFT));

            service.create(request);

            verifyNoInteractions(notificationPort);
        }

        @Test
        @DisplayName("Should reject duplicate event name on creation")
        void givenDuplicateName_whenCreating_thenDoesNotSave() {
            // [MOCKITO] willThrow em método void: doThrow(...).when(mock).method(...)
            // Sintaxe BDD: willThrow(...).given(mock).method(...)
            willThrow(new BusinessRuleException("dup", "DUPLICATE_NAME"))
                    .given(uniquenessChecker).assertNameIsAvailable("Rock in Sampa 2026");

            assertThatThrownBy(() -> service.create(aCreateRequest("Rock in Sampa 2026")))
                    .isInstanceOf(BusinessRuleException.class);

            // never() é mais expressivo que verifyNoInteractions quando você quer
            // apontar um método específico
            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update{
        @Test
        @DisplayName("should update editable fields of a DRAFT event")
        void givenDraft_whenUpdating_thenFieldsAreChanged(){
            Event event = anEvent(EventStatus.DRAFT);

            willAnswer(invocation -> {
                Event e = invocation.getArgument(0);
                UpdateEventRequest req = invocation.getArgument(1);
                e.setName(req.name());
                return null;
            }).given(eventMapper).updateEntity(any(Event.class), any(UpdateEventRequest.class));
            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            given(eventMapper.toResponse(any(Event.class))).willReturn(anEventResponse(EventStatus.DRAFT));

            service.update(1L, anUpdateRequest("Renamed Event"));

            assertThat(event.getName()).isEqualTo("Renamed Event");

            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject updating restricted fields of a PUBLISHED event")
        void givenPublishedEvent_whenUpdatingRestrictedField_thenThrows(){
            Event event = anEvent(EventStatus.PUBLISHED);
            String originalName = event.getName();

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);

            assertThatThrownBy(() -> service.update(1L, anUpdateRequest("Renamed Event")))
                    .isInstanceOf(BusinessRuleException.class)
                            .extracting("errorCode")
                                    .isEqualTo("EVENT_FIELD_NOT_EDITABLE");

            assertThat(event.getName()).isEqualTo(originalName);
            verifyNoInteractions(eventMapper);
        }

        @Test
        @DisplayName("should allow updating description of a PUBLISHED event")
        void givenPublishedEvent_whenUpdatingOnlyDescription_thenSucceeds(){
            Event event = anEvent(EventStatus.PUBLISHED);

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            given(eventMapper.toResponse(event)).willReturn(anEventResponse(EventStatus.PUBLISHED));

            UpdateEventRequest request = anUpdateRequestKeeping(event, "New description");

            service.update(1L, request);

            verify(eventMapper).updateEntity(event, request);
        }

        @Test
        @DisplayName("should check name availability excluding the event itself")
        void givenNewName_whenUpdating_thenUniquenessIsCheckedForUpdate(){
            Event event = anEvent(EventStatus.DRAFT);

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            given(eventMapper.toResponse(event)).willReturn(anEventResponse(EventStatus.DRAFT));

            service.update(1L, anUpdateRequest("Renamed Event"));

            verify(uniquenessChecker).assertNameIsAvailableForUpdate("Renamed Event", 1L);
        }
    }

    @Nested
    @DisplayName("publish")
    class Publish{
        @Test
        @DisplayName("should publish a DRAFT event and notify")
        void givenDraftEvent_whenPublishing_thenStatusBecomesPublishedAndNotificationIsSent(){
            // ═══ ARRANGE ═══
            Event event = anEvent(EventStatus.DRAFT);

            // [MOCKITO - BDD] given(...).willReturn(...) é o mesmo que when().thenReturn(),
            // com vocabulário Given/When/Then. Combina com o nome dos seus testes.
            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            given(eventMapper.toResponse(event)).willReturn(anEventResponse(EventStatus.PUBLISHED));

            // ═══ ACT ═══
            EventResponse result = service.publish(1L);

            // ═══ ASSERT ═══
            // 1. ESTADO: a entidade real mudou. Este é o assert que importa —
            //    verifica COMPORTAMENTO, não interação.
            assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);

            // 2. RESULTADO retornado
            assertThat(result.status()).isEqualTo(EventStatus.PUBLISHED);

            // 3. EFEITO COLATERAL: a notificação saiu.
            //    verify() é legítimo aqui porque notificar é o efeito observável —
            //    não há retorno para assertar.
            verify(notificationPort).notifyEventPublished(event);

            // ⚠️ Note que NÃO verificamos repository.save(). O dirty checking cuida
            // do UPDATE, e o service não chama save() — verificar isso testaria
            // implementação. O que importa é o status ter mudado.
        }

        @Test
        @DisplayName("should not notify when publishing fails")
        void givenAlreadyPublishedEvent_whenPublishing_thenThrowsAndDoesNotNotify(){
            Event event = anEvent(EventStatus.PUBLISHED);
            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);

            assertThatThrownBy(() -> service.publish(1L))
                    .isInstanceOf(BusinessRuleException.class);

            // 🎯 O assert mais valioso deste teste: garantir que NADA aconteceu.
            // Sem ele, um bug que notifica antes de validar passaria despercebido —
            // e o cliente receberia "seu evento foi publicado" de um evento que
            // não foi publicado. Testar o caminho infeliz é testar o que NÃO deve
            // acontecer.
            verifyNoInteractions(notificationPort);
        }

        @Test
        @DisplayName("should not publish an event that has already started")
        void givenEventInThePast_whenPublishing_thenThrows(){
            Event event = anEvent(EventStatus.DRAFT);

            // Antes do NOW congelado. Determinístico graças ao Clock injetado —
            // este é exatamente o teste que era IMPOSSÍVEL antes de tirarmos
            // o Instant.now() de dentro de Event.publish().
            event.setStartsAt(NOW.minus(Duration.ofDays(1)));
            event.setEndsAt(NOW.minus(Duration.ofHours(20)));

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);

            assertThatThrownBy(() -> service.publish(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode")
                    .isEqualTo("EVENT_ALREADY_STARTED");

            assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel{
        @Test
        @DisplayName("should cancel event with reason and forward it to notification")
        void givenPublishedEvent_whenCancellingWithReason_thenReasonIsForwarded(){
            Event event = anEvent(EventStatus.PUBLISHED);

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            given(eventMapper.toResponse(event)).willReturn(anEventResponse(EventStatus.CANCELLED));

            service.cancel(1L, "Venue unavailable");

            assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);

            // A policy foi consultada ANTES da mudança de estado
            verify(cancellationPolicy).ensureCanBeCancelled(event, "Venue unavailable");

            // [MOCKITO] ArgumentCaptor: captura o argumento REAL que foi passado.
            // Use quando precisar assertar sobre o VALOR, não só sobre "foi chamado".
            // Este teste pegaria o bug do "TODO" que estava no lugar do reason.
            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationPort).notifyEventCancelled(any(Event.class), reasonCaptor.capture());
            assertThat(reasonCaptor.getValue()).isEqualTo("Venue unavailable");
        }

        @Test
        @DisplayName("should keep status unchanged when policy rejects cancellation")
        void givenPolicyRejects_whenCancelling_thenStatusUnchangedAndNoNotification(){
            Event event = anEvent(EventStatus.PUBLISHED);

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            willThrow(new BusinessRuleException("started", "EVENT_ALREADY_STARTED"))
                    .given(cancellationPolicy).ensureCanBeCancelled(any(), any());

            assertThatThrownBy(() -> service.cancel(1L, "reason"))
                    .isInstanceOf(BusinessRuleException.class);

            // 🎯 O TESTE MAIS VALIOSO DESTA CLASSE.
            // Prova a ORDEM: consulta a policy ANTES de mutar o estado.
            // Se a ordem se invertesse num refactor, o evento seria cancelado
            // e SÓ DEPOIS a exception subiria — deixando a entidade
            // inconsistente (e persistida, se a transação não abortasse).
            assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
            verifyNoInteractions(notificationPort);
        }
    }

    @Nested
    @DisplayName("reschedule")
    class Reschedule{
        @Test
        @DisplayName("should notify only when the event was already PUBLISHED")
        void givenPublishedEvent_whenRescheduling_thenNotifies(){
            Event event = anEvent(EventStatus.PUBLISHED);

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            given(eventMapper.toResponse(any(Event.class))).willReturn(anEventResponse(EventStatus.PUBLISHED));

            service.reschedule(1L, aRescheduleRequest(NOW.plus(Duration.ofDays(45))));

            verify(reschedulePolicy).reschedule(event, NOW.plus(Duration.ofDays(45)), null);
            verify(notificationPort).notifyEventRescheduled(event);
        }

        @Test
        @DisplayName("should not notify when rescheduling a DRAFT event")
        void givenDraftEvent_whenRescheduling_thenDoesNotNotify(){
            Event event = anEvent(EventStatus.DRAFT);

            given(eventQueryService.getRequiredEvent(1L)).willReturn(event);
            given(eventMapper.toResponse(any(Event.class))).willReturn(anEventResponse(EventStatus.DRAFT));

            service.reschedule(1L, aRescheduleRequest(NOW.plus(Duration.ofDays(45))));

            verifyNoInteractions(notificationPort);
        }
    }

    private Event anEvent(EventStatus status){
        Event event = new Event();
        event.setId(1L);
        event.setName("Test Event");
        event.setDescription("Test Description");
        event.setVenue("Test Venue");
        event.setCity("Sao Paulo");
        event.setOrganizerName("Test Organizer");
        event.setStartsAt(DEFAULT_STARTS_AT);
        event.setEndsAt(DEFAULT_ENDS_AT);
        event.setTicketPrice(new BigDecimal("100.00"));
        event.setTotalTickets(100);
        event.setAvailableTickets(100);
        event.setStatus(status);
        return event;
    }

    private EventResponse anEventResponse(EventStatus status){
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
                status,
                NOW,
                NOW
        );
    }

    private CreateEventRequest aCreateRequest(String name) {
        return aCreateRequest(name, new BigDecimal("100.00"), DEFAULT_STARTS_AT);
    }

    private CreateEventRequest aCreateRequest(String name, BigDecimal price, Instant startsAt) {
        return new CreateEventRequest(
                name,
                "Test Description",
                "Test Venue",
                "Sao Paulo",
                "Test Organizer",
                startsAt,
                startsAt.plus(Duration.ofHours(4)),
                price,
                100
        );
    }

    private UpdateEventRequest anUpdateRequest(String name){
        return new UpdateEventRequest(
                name,
                "Update Description",
                "Test Venue",
                "Sao Paulo",
                "Test Organizer",
                DEFAULT_STARTS_AT,
                DEFAULT_ENDS_AT,
                new BigDecimal("100.00")
        );
    }

    private UpdateEventRequest anUpdateRequestKeeping(Event event, String description){
        return new UpdateEventRequest(
                event.getName(),
                description,
                event.getVenue(),
                event.getCity(),
                event.getOrganizerName(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getTicketPrice()
        );
    }

    private RescheduleEventRequest aRescheduleRequest(Instant newStartsAt){
        return new RescheduleEventRequest(newStartsAt, null);
    }
}