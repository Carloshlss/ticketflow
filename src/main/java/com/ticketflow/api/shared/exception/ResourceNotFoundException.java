package com.ticketflow.api.shared.exception;

/**
 * [CLEAN CODE] Exceção de DOMÍNIO: expressa o problema na linguagem do
 * negócio, não do framework. O service lança isto; quem decide que o HTTP
 * disso é 404 é o GlobalExceptionHandler.
 *
 * Isso é [SOLID - Dependency Inversion] na prática: o service não conhece HTTP.
 * Ele poderia ser chamado por um consumer Kafka (Fase 9) ou por um job, e a
 * exceção continua fazendo sentido.
 *
 * RuntimeException (unchecked) e não Exception (checked), porque:
 *   1. o Spring só faz ROLLBACK automático em unchecked (detalhe importante!)
 *   2. não poluímos as assinaturas com throws
 *   3. quem chama geralmente não tem como se recuperar disso
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message){
        super(message);
    }

    /**
     * [FACTORY METHOD] Padroniza a mensagem em toda a aplicação, evitando
     * 40 variações de texto para o mesmo problema.
     * Uso: throw ResourceNotFoundException.of("Event", 42);
     */
    public static ResourceNotFoundException of(String resourceName, Object identifier){
        return new ResourceNotFoundException(
                "%s not found with identifier: %s".formatted(resourceName, identifier)
        );  // [JAVA 15+] String.formatted() — mais legível que String.format
    }
}
