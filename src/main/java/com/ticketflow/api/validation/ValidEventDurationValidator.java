package com.ticketflow.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@RequiredArgsConstructor
public class ValidEventDurationValidator implements ConstraintValidator<ValidEventDuration, EventIntervalAware> {
    final int MAX_EVENT_DURATION_HOURS = 72;

    @Override
    public boolean isValid(EventIntervalAware dto, ConstraintValidatorContext context){
        if(dto == null || dto.startsAt() == null || dto.endsAt() == null){
            return true;
        }

        Duration duration = Duration.between(dto.startsAt(), dto.endsAt());

        // Ordem das datas é responsabilidade do @AssertTrue. Se estiver invertida,
        // não é ESTE validador que reclama — evita duas mensagens para o mesmo erro.
        if(duration.isNegative()){
            return true;
        }

        return duration.toHours() <= MAX_EVENT_DURATION_HOURS;
    }
}
