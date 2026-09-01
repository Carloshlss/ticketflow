package com.ticketflow.api.event;

import com.ticketflow.api.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class EventCancellationPolicy {
    public void check(Event event, String reason, Instant now){
        statusAbleToCancel(event);
        eventStartsCancellation(event, reason, now);
    }

    private static void eventStartsCancellation(Event event, String reason, Instant now) {
        if(now.isAfter(event.getStartsAt())){
            throw new BusinessRuleException(
                    "Cannot publish an event that already started", "EVENT_ALREADY_STARTED");
        }
        Duration durationToStart = Duration.between(now, event.getStartsAt());
        if(durationToStart.toHours() < 24
                && (reason == null || reason.isBlank())){
            throw new BusinessRuleException(
                    "Cannot cancel an event less then 24 hours to start without a reason", "EVENT_NOT_EDITABLE");
        }
    }

    private static void statusAbleToCancel(Event event) {
        EventStatus status = event.getStatus();
        if(!status.canTransitionTo(EventStatus.CANCELLED)){
            throw new BusinessRuleException(
                    "Cannot update an event with status " + event.getStatus(), "EVENT_NOT_EDITABLE");
        }
    }
}
