package com.ticketflow.api.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleEventRequest(
        @NotNull(message = "New start date is required")
        @Future(message = "New start date must be in the future")
        Instant newStartsAt,

        // Opcional: quando ausente, a duração original é preservada
        Instant newEndsAt
) {
}
