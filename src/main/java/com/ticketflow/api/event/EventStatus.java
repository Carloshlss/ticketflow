package com.ticketflow.api.event;

import java.util.EnumSet;
import java.util.Set;

public enum EventStatus {
    DRAFT,      // criado, não visível ao público
    PUBLISHED,  // à venda
    SOLD_OUT,   // esgotado
    CANCELLED,  // cancelado
    FINISHED;    // já ocorreu

    public Set<EventStatus> allowedTransitions(){
        return switch (this){
            case DRAFT -> EnumSet.of(PUBLISHED, CANCELLED);
            case PUBLISHED -> EnumSet.of(SOLD_OUT, CANCELLED, FINISHED);
            case SOLD_OUT -> EnumSet.of(PUBLISHED, CANCELLED, FINISHED);
            case CANCELLED, FINISHED -> EnumSet.noneOf(EventStatus.class);
        };
    }

    public boolean canTransitionTo(EventStatus target){
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal(){
        return allowedTransitions().isEmpty();
    }

    public boolean isVisibleToPublic(){
        return this == PUBLISHED || this == SOLD_OUT;
    }

    public boolean isEditable(){
        return this == DRAFT || this == PUBLISHED;
    }
}
