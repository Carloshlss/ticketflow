package com.ticketflow.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidEventDurationValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEventDuration {
    String message() default "Max event duration is 72 hours";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
