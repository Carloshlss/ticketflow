package com.ticketflow.api.notification;

import com.ticketflow.api.event.Event;
import com.ticketflow.api.event.port.EventNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "ticketflow.notification.adapter",
        havingValue = "logging",
        matchIfMissing = true)
public class LoggingNotificationAdapter implements EventNotificationPort {
    @Override
    public void notifyEventPublished(Event event) {
        log.info("[NOTIFICATION] Event published: {} in {}", event.getName(), event.getCity());
    }

    @Override
    public void notifyEventCancelled(Event event, String reason) {
        log.info("[NOTIFICATION] Event cancelled: {} | reason: {}", event.getName(), reason);
    }

    @Override
    public void notifyEventRescheduled(Event event){
        log.info("[NOTIFICATION] Event Reschedule: {} to {}", event.getName(), event.getStartsAt());
    }
}
