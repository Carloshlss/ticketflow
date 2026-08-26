package com.ticketflow.api.event;

public enum EventStatus {
    DRAFT,      // criado, não visível ao público
    PUBLISHED,  // à venda
    SOLD_OUT,   // esgotado
    CANCELLED,  // cancelado
    FINISHED    // já ocorreu
}
