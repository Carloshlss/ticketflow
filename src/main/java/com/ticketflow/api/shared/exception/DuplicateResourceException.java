package com.ticketflow.api.shared.exception;

/** Violação de unicidade — mapeada para 409 Conflict. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message){
        super(message);
    }

    public static DuplicateResourceException of(String resourceName, String field, Object value){
        return new DuplicateResourceException("%s already exists with %s: %s".formatted(resourceName, field, value));
    }
}
