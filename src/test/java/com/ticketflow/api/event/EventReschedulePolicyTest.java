package com.ticketflow.api.event;

import com.ticketflow.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventReschedulePolicy")
public class EventReschedulePolicyTest {
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private EventReschedulePolicy policy;

    @BeforeEach
    void setUp(){
        policy = new EventReschedulePolicy(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("should preserve original duration when only new start is provided")
    void givenOnlyNewStart_whenRescheduling_thenOriginalDurationIsPreserved(){
        Event event = anEvent(EventStatus.PUBLISHED,
                NOW.plus(Duration.ofDays(30)),
                NOW.plus(Duration.ofDays(30)).plus(Duration.ofHours(4)));

        Instant newStart = NOW.plus(Duration.ofDays(45));

        policy.reschedule(event, newStart, null);

        assertThat(event.getStartsAt()).isEqualTo(newStart);
        assertThat(event.getEndsAt()).isEqualTo(newStart.plus(Duration.ofHours(4)));
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED", "SOLD_OUT"})
    @DisplayName("should reject rescheduling for non-editable statuses")
    void givenNonEditableStatus_whenRescheduling_thenThrows(EventStatus status){
        Event event = anEvent(status,
                NOW.plus(Duration.ofHours(30)),
                NOW.plus(Duration.ofDays(30)).plus(Duration.ofHours(4)));

        assertThatThrownBy(() -> policy.reschedule(event, NOW.plus(Duration.ofDays(45)), null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("EVENT_NOT_RESCHEDULABLE");
    }

    @Test
    @DisplayName("should reject an end date before the start date")
    void givenEndBeforeStart_whenRescheduling_thenThrows(){
        Event event = anEvent(EventStatus.PUBLISHED,
                NOW.plus(Duration.ofDays(30)),
                NOW.plus(Duration.ofDays(30)).plus(Duration.ofHours(4)));

        Instant newStart = NOW.plus(Duration.ofDays(45));
        Instant invalidEnd = newStart.minus(Duration.ofHours(1));

        assertThatThrownBy(() -> policy.reschedule(event, newStart, invalidEnd))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_EVENT_PERIOD");
    }

    private Event anEvent(EventStatus status, Instant startsAt, Instant endsAt){
        Event event = new Event();
        event.setId(1L);
        event.setName("Test Event");
        event.setVenue("Test Venue");
        event.setCity("Sao Paulo");
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        event.setTicketPrice(new BigDecimal("100.00"));
        event.setTotalTickets(100);
        event.setAvailableTickets(100);
        event.setStatus(status);
        return event;
    }
}
