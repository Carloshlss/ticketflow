package com.ticketflow.api.notification;

import com.ticketflow.api.event.Event;

/**
 * [DIP] Esta interface pertence ao DOMÍNIO, não à infraestrutura.
 *
 * Note o vocabulário: "notifyEventPublished", não "sendEmail".
 * A interface descreve a INTENÇÃO DE NEGÓCIO, não o meio técnico.
 * Isso é crucial: se ela se chamasse sendEmail(), uma implementação por
 * SMS ou push seria absurda — a abstração teria vazado o detalhe.
 *
 * [ARQUITETURA HEXAGONAL] Isto é uma "PORTA DE SAÍDA" (driven port).
 * Vou usar esse nome de propósito para você já se familiarizar.
 */
public interface EventNotificationPort {
    void notifyEventPublished(Event event);

    void notifyEventCancelled(Event event, String reason);
}
