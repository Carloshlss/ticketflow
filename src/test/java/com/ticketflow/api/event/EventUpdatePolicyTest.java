package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.UpdateEventRequest;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventUpdatePolicy")
class EventUpdatePolicyTest {
    private EventUpdatePolicy updatePolicy;

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final Instant ENDS = NOW.plus(Duration.ofHours(4));
    private static final BigDecimal PRICE = new BigDecimal("100.00");

    @BeforeEach
    void setUp(){
        updatePolicy = new EventUpdatePolicy();
    }

    // ─────────── DRAFT: tudo editável ───────────

    @Test
    @DisplayName("should allow changing any field when DRAFT")
    void givenDraftEvent_whenChangingImmutableFields_thenNoExceptionIsThrown(){
        Event event = anEvent(EventStatus.DRAFT);

        UpdateEventRequest request = aRequest()
                .city("Rio de Janeiro")
                .venue("Other Arena")
                .ticketPrice(new BigDecimal("999.00"))
                .startsAt(NOW.plus(Duration.ofDays(30)))
                .build();

        assertThatCode(() -> updatePolicy.ensureCanBeUpdated(event, request))
                .doesNotThrowAnyException();
    }

    // ─────────── PUBLISHED: campos travados ───────────

    static Stream<Arguments> immutableFieldsWhenPublished(){
        return Stream.of(
                Arguments.of("city", aRequest().city("Rio de Janeiro").build()),
                Arguments.of("venue", aRequest().venue("Other Arena").build()),
                Arguments.of("ticketPrice", aRequest().ticketPrice(new BigDecimal("999.00")).build()),
                Arguments.of("startsAt", aRequest().startsAt(NOW.plus(Duration.ofDays(30))).build()),
                Arguments.of("endsAt", aRequest().endsAt(ENDS.plus(Duration.ofHours(2))).build())
        );
    }

    @ParameterizedTest(name = "field ''{0}'' must be rejected")
    @MethodSource("immutableFieldsWhenPublished")
    @DisplayName("should reject immutable field change when PUBLISHED")
    void givenPublishedEvent_whenChangingImmutableField_thenThrows(String field, UpdateEventRequest request){
        Event event = anEvent(EventStatus.PUBLISHED);

        assertThatThrownBy(() -> updatePolicy.ensureCanBeUpdated(event, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("EVENT_FIELD_NOT_EDITABLE");
    }

    @Test
    @DisplayName("should allow description change when PUBLISHED")
    void givenPublishedEvent_whenChangingOnlyDescription_thenNoExceptionIsThrown(){
        Event event = anEvent(EventStatus.PUBLISHED);
        UpdateEventRequest request = aRequest().description("New description").build();

        assertThatCode(() -> updatePolicy.ensureCanBeUpdated(event, request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should allow request identical to current state when PUBLISHED")
    void givenPublishedEvent_whenRequestIsUnchanged_thenNoExceptionIsThrown(){
        Event event = anEvent(EventStatus.PUBLISHED);

        assertThatCode(() -> updatePolicy.ensureCanBeUpdated(event, aRequest().build()))
                .doesNotThrowAnyException();
    }

    // ─────────── Status terminais ───────────

    @ParameterizedTest(name = "status {0} must be rejected")
    @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
    @DisplayName("should reject any update on terminal status")
    void givenTerminalStatus_whenCheckingUpdate_thenThrows(EventStatus status){
        Event event = anEvent(status);

        assertThatThrownBy(() -> updatePolicy.ensureCanBeUpdated(event, aRequest().build()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("EVENT_NOT_EDITABLE");
    }

    private Event anEvent(EventStatus status){
        Event event = new Event();
        event.setId(1L);
        event.setName("Test Event");
        event.setDescription("Test Description");
        event.setVenue("Test Venue");
        event.setCity("Sao Paulo");
        event.setStartsAt(NOW);
        event.setEndsAt(NOW.plus(Duration.ofHours(4)));
        event.setTicketPrice(new BigDecimal("100.00"));
        event.setTotalTickets(100);
        event.setAvailableTickets(100);
        event.setStatus(status);
        return event;
    }

    private static RequestBuilder aRequest(){
        return new RequestBuilder();
    }

    private static class RequestBuilder{
        private String name = "Test Event";
        private String description = "Test Description";
        private String venue = "Test Venue";
        private String city = "Sao Paulo";
        private String organizerName = "Test Organizer";
        private Instant startsAt = NOW;
        private Instant endsAt = ENDS;
        private BigDecimal ticketPrice = PRICE;

        RequestBuilder name(String v){this.name = v; return this;}
        RequestBuilder description(String v){this.description = v; return this;}
        RequestBuilder venue(String v){this.venue = v; return this;}
        RequestBuilder city(String v){this.city = v; return this;}
        RequestBuilder organizerName(String v){this.organizerName = v; return this;}
        RequestBuilder startsAt(Instant v){this.startsAt = v; return this;}
        RequestBuilder endsAt(Instant v){this.endsAt = v; return this;}
        RequestBuilder ticketPrice(BigDecimal v){this.ticketPrice = v; return this;}

        UpdateEventRequest build(){
            return new UpdateEventRequest(name, description, venue, city, organizerName, startsAt, endsAt, ticketPrice);
        }
    }
}