package com.ticketflow.api.notification;

import com.ticketflow.api.event.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
public class LoggingNotificationAdapter implements EventNotificationPort {
    @Override
    public void notifyEventPublished(Event event) {
        log.info("[NOTIFICATION] Event published: {} in {}", event.getName(), event.getCity());
    }

    @Override
    public void notifyEventCancelled(Event event, String reason) {
        log.info("[NOTIFICATION] Event cancelled: {} | reason: {}", event.getName(), reason);
    }
}
