package com.ticketflow.api.event;

import org.hibernate.AssertionFailure;

/**
 * [CLEAN CODE] Constantes de domínio nomeadas, num só lugar.
 *
 * O problema do "magic number": ao ver `if (duration.toHours() > 72)`,
 * o leitor não sabe se 72 é uma regra de negócio, um limite técnico ou um
 * chute. E quando o valor mudar, você faz busca por "72" no projeto —
 * encontrando 72 em contextos totalmente diferentes.
 *
 * final class + construtor privado = classe utilitária não instanciável
 * nem herdável. Comunica "isto é só um namespace de constantes".
 */
public class EventPolicy {
    public static final int MAX_EVENT_DURATION_HOURS = 72;
    public static final int MIN_HOURS_BEFORE_PUBLISH = 24;
    public static final int MAX_TICKETS_PER_ORDER = 10;
    public static final int MAX_TOTAL_TICKETS = 1_000_000;   // [JAVA 7] _ separador

    private EventPolicy(){
        throw new AssertionFailure("EventPolicy is not instantiable");
    }
}
