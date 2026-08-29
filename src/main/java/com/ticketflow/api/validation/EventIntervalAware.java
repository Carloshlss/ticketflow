package com.ticketflow.api.validation;

import java.time.Instant;

public interface EventIntervalAware {
    Instant startsAt();
    Instant endsAt();
}
