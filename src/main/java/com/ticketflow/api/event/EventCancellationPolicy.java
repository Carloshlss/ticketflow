package com.ticketflow.api.event;

import com.ticketflow.api.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * [SRP] Responsabilidade única: DECIDIR se um evento pode ser cancelado.
 * Ela não cancela, não notifica, não persiste. Só decide.
 *
 * [SOLID] Extraída do EventCommandService porque é uma regra que:
 *   - tem seus próprios critérios (status, janela de tempo, motivo)
 *   - muda por motivos próprios (o jurídico redefine a janela de 24h)
 *   - precisa ser testada isoladamente
 *
 * [TESTABILIDADE] Depende de Clock, não de Instant.now(). Em teste unitário:
 *   new EventCancellationPolicy(Clock.fixed(instanteEscolhido, ZoneOffset.UTC))
 * Sem Spring, sem banco, roda em milissegundos.
 */
@Component
@RequiredArgsConstructor
public class EventCancellationPolicy {
    /** [CLEAN CODE] Constante nomeada em vez do literal 24 espalhado no código. */
    private static final Duration REASON_REQUIRED_WINDOW = Duration.ofHours(24);

    private final Clock clock;

    public void ensureCanBeCancelled(Event event, String reason){
        Instant now = clock.instant();

        validateStatusAllowsCancellation(event);
        validateEventHasNotStarted(event, now);
        validateReasonWhenCloseToStart(event, reason, now);
    }

    private void validateStatusAllowsCancellation(Event event){
        if(!event.getStatus().canTransitionTo(EventStatus.CANCELLED)){
            throw new BusinessRuleException(
                    "Cannot cancel an event with status %s".formatted(event.getStatus()), "INVALID_STATUS_TRANSITION");
        }
    }

    private void validateEventHasNotStarted(Event event, Instant now){
        if(now.isAfter(event.getStartsAt())){
            throw new BusinessRuleException(
                    "Cannot cancel an event that already started", "EVENT_ALREADY_STARTED");
        }
    }

    private void validateReasonWhenCloseToStart(Event event, String reason, Instant now){
        Duration timeUntilStart = Duration.between(now, event.getStartsAt());

        boolean isCloseToStart = timeUntilStart.compareTo(REASON_REQUIRED_WINDOW) < 0;
        boolean hasNoReason = reason == null || reason.isBlank();

        if(isCloseToStart && hasNoReason){
            throw new BusinessRuleException(
                    "A reason is required to cancel an event starting in less than %d hours"
                            .formatted(REASON_REQUIRED_WINDOW.toHours()),
                    "CANCELLATION_REASON_REQUIRED");
        }
    }
}
