package com.ticketflow.api.event;

import com.ticketflow.api.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * [SRP] Decide e executa o reagendamento de um evento.
 *
 * Construída via TDD: cada validação abaixo nasceu de um teste que falhou
 * antes de ela existir. Consequência mensurável: cobertura de 100% das
 * ramificações, sem nenhum esforço extra de "escrever testes depois".
 */
@Component
@RequiredArgsConstructor
public class EventReschedulePolicy {

    /**
     * [CLEAN CODE] Set nomeado em vez de 'status == DRAFT || status == PUBLISHED'
     * espalhado. A regra ganha nome e fica num único lugar.
     */
    private static final Set<EventStatus> RESCHEDULABLE_STATUSES = Set.of(EventStatus.DRAFT, EventStatus.PUBLISHED);

    private final Clock clock;

    @Transactional
    public void reschedule(Event event, Instant newStartsAt, Instant newEndsAt){
        Instant now = clock.instant();

        // [CLEAN CODE] O método público lê como a especificação do requisito.
        // Cada passo tem nome; os detalhes ficam nos métodos privados.
        // Este é o "Composed Method pattern".
        validateStatusIsReschedulable(event);
        validateNewStartIsInTheFuture(newStartsAt, now);

        Instant resolvedEndsAt = resolveEndDate(event, newStartsAt, newEndsAt);
        validatePeriodIsConsistent(newStartsAt, resolvedEndsAt);

        applyNewDates(event, newStartsAt, resolvedEndsAt);
    }

    private void validateStatusIsReschedulable(Event event){
        if(!RESCHEDULABLE_STATUSES.contains(event.getStatus())){
            throw new BusinessRuleException(
                    "Cannot reschedule an event with status %s".formatted(event.getStatus()),
                    "EVENT_NOT_RESCHEDULABLE");
        }
    }

    private void validateNewStartIsInTheFuture(Instant newStartsAt, Instant now){
        if(newStartsAt.isBefore(now)){
            throw new BusinessRuleException(
                    "Cannot reschedule an event to a past date",
                    "RESCHEDULE_DATE_IN_PAST");
        }
    }

    /**
     * Se o fim não foi informado, PRESERVA a duração original — comportamento
     * definido pelo primeiro teste que escrevemos.
     */
    private Instant resolveEndDate(Event event, Instant newStartsAt, Instant newEndsAt){
        if(newEndsAt != null){
            return newEndsAt;
        }
        Duration originalDuration = Duration.between(event.getStartsAt(), event.getEndsAt());
        return newStartsAt.plus(originalDuration);
    }

    private void validatePeriodIsConsistent(Instant startsAt, Instant endsAt){
        if(!endsAt.isAfter(startsAt)){
            throw new BusinessRuleException(
                    "Event end date must be after its start date",
                    "INVALID_EVENT_PERIOD");
        }
    }

    private void applyNewDates(Event event, Instant startsAt, Instant endsAt){
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
    }
}
