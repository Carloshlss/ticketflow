package com.ticketflow.api.shared.exception;

import com.ticketflow.api.shared.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * [SPRING MVC] @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
 *
 * Funciona como um try/catch GLOBAL: qualquer exceção que escape de qualquer
 * @RestController cai aqui, e o método com o @ExceptionHandler correspondente
 * trata. Se houver hierarquia, o Spring escolhe o handler MAIS ESPECÍFICO.
 *
 * [AOP] É interceptação — seus controllers e services não sabem que isto
 * existe. Zero acoplamento.
 *
 * [SOLID - Single Responsibility] Traduzir exceção de domínio em resposta
 * HTTP é UMA responsabilidade, e vive em UM lugar. Sem isto, cada método de
 * controller teria try/catch duplicado.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    // ============ 404 NOT FOUND ============
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handlerResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request){
        log.warn("Resource not found: {}", ex.getMessage());

        // WARN e não ERROR: recurso inexistente é operação normal do dia a dia.
        // Poluir o log de ERROR com 404 faz você ignorar erros de verdade.
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request, null);
    }

    // ============ 409 CONFLICT ============
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request){
        log.warn("Business rule violated [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request){
        log.warn("Duplicate resource: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), request, null);
    }

    /**
     * [JPA] Lock otimista falhou (o @Version da Fase 2!).
     * 409 é o status correto: o estado do recurso mudou sob seus pés.
     * O cliente deve recarregar e tentar de novo.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request){
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "This resource was modified by another request. Please reload and retry",
                request, null);
    }

    // ============ 400 BAD REQUEST ============

    /**
     * [BEAN VALIDATION] Lançada quando o @Valid de um @RequestBody falha.
     * Traduzimos cada violação em um FieldError, para o front destacar
     * exatamente os inputs errados.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request){
        List<ApiError.FieldError> fieldErrors = new ArrayList<>(
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> new ApiError.FieldError(
                                fe.getField(),
                                fe.getRejectedValue(),
                                fe.getDefaultMessage()))
                        .toList()
        );

        ex.getBindingResult().getGlobalErrors()
                        .forEach(ge -> fieldErrors.add(new ApiError.FieldError(
                                "_object",
                                null,
                                ge.getDefaultMessage()
                        )));

        log.warn("Validation failed for {}: {} error(s)", request.getRequestURI(), fieldErrors.size());

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "One or more fields are invalid", request, fieldErrors);
    }

    /**
     * [JACKSON] JSON malformado ou tipo incompatível.
     * ⚠️ NÃO devolvemos ex.getMessage(): ele contém nomes de classes internas
     * e trechos do payload. Isso é vazamento de informação.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request){
        log.warn("Malformed request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MALVORMED_REQUEST",
                "Request body is malformed or contains invalid values", request, null);
    }

    /** Ex.: GET /events/abc quando o @PathVariable é Long. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request){
        String message = "Parameter '%s' has an invalid value: '%s'".formatted(ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, request, null);
    }

    /** @RequestParam obrigatório ausente. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request){
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '%s' is missing".formatted(ex.getParameterName()), request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request){
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(
                        v.getPropertyPath().toString(),
                        v.getInvalidValue(),
                        v.getMessage()))
                .toList();

        log.warn("Parameter validation failed for {}", request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "one or more parameters are invalid",
                request, fieldErrors);
    }

    /**
     * [SPRING DATA] Constraint do BANCO violada (unique, not null, FK, check).
     * É a nossa REDE DE SEGURANÇA: se o check TOCTOU do service escapou por
     * concorrência, o banco barra e nós respondemos 409 de forma elegante.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request){

        // ERROR aqui: normalmente indica falha de validação na aplicação.
        log.error("Data integrity violation", ex);
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "The operation violates a data integrity constraint", request, null);
    }

    // ============ 500 — O CATCH-ALL ============

    /**
     * ⚠️ O handler MAIS IMPORTANTE e o mais fácil de escrever errado.
     *
     * Regras:
     *   1. logue a exceção COMPLETA (com stack trace) — é o único registro
     *   2. NUNCA devolva ex.getMessage() ao cliente: pode conter SQL, caminho
     *      de arquivo, host interno. É vetor de reconhecimento para atacante.
     *   3. devolva um traceId genérico, para o suporte cruzar com o log
     *
     * Por ser Exception (a mais genérica), só é escolhida quando nenhum
     * handler específico casa.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request){
        String traceId = MDC.get("requestId");
        log.error("Unexpected error traceId={} path={}", traceId, request.getRequestURI(), ex);

        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please contact support with the trace id.",
                request.getRequestURI(),
                traceId,
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /** [CLEAN CODE] Fábrica privada: elimina a repetição em 9 handlers. */
    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest request, List<ApiError.FieldError> fieldErrors){
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                null,
                fieldErrors
        );
        return ResponseEntity.status(status).body(error);
    }
}
