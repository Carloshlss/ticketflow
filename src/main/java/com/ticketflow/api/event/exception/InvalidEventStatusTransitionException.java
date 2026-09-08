package com.ticketflow.api.event.exception;

import com.ticketflow.api.event.EventStatus;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import lombok.Getter;

/**
 * [CLEAN CODE] Exceção ESPECÍFICA em vez de BusinessRuleException genérica.
 * Ganhos:
 *   - a mensagem é montada num lugar só, sempre igual
 *   - carrega os DADOS (from/to), não só texto — o handler pode usá-los
 *   - o handler pode tratá-la de forma diferente se um dia precisar
 */
@Getter
public class InvalidEventStatusTransitionException extends BusinessRuleException {
    private final EventStatus from;
    private final EventStatus to;

    public InvalidEventStatusTransitionException(EventStatus from, EventStatus to) {
        super("Cannot transition from %s to%s".formatted(from, to), "INVALID_STATUS_TRANSITION");
        this.from = from;
        this.to = to;
    }

}
