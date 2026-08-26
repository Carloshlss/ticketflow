package com.ticketflow.api.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.validation.FieldError;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,              // 400, 404, 409...
        String error,           // "Bad Request" (nome do status)
        String code,            // código estável, para o cliente decidir
        String message,         // legível por humano
        String path,            // qual endpoint falhou
        String traceId,         // correlação com o log (Fase 14: distributed tracing)
        List<FieldError> fieldErrors   // só em erro de validação
) {
    /** Erro por campo. Permite o front destacar o input exato. */
    public record FieldError(String field, Object rejectedValue, String message){}
}
