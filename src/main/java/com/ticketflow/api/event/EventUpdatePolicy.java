package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.UpdateEventRequest;
import com.ticketflow.api.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * [SRP] Responde a UMA pergunta: quais campos podem mudar neste status?
 *
 * Por que não deixar isso no service: a regra é comparativa (estado atual
 * vs. requisitado) e tem várias ramificações — exatamente o tipo de lógica
 * que precisa de teste unitário puro e rápido. No service, ela só seria
 * testável arrastando mocks de repositório e mapper.
 */
@Component
public class EventUpdatePolicy {
    public void ensureCanBeUpdated(Event event, UpdateEventRequest request){

        /**
         * ⚠️ Executa ANTES de qualquer mutação da entidade.
         * O dirty checking do Hibernate persiste no commit qualquer alteração
         * feita numa entidade gerenciada. Validar DEPOIS de mutar significaria
         * gravar dado inválido caso a transação não abortasse.
         */
        if(event.getStatus() == EventStatus.CANCELLED
        || event.getStatus() == EventStatus.FINISHED){
            throw new BusinessRuleException(
                    "A terminal event cannot be updated", "EVENT_NOT_EDITABLE");
        }

        // DRAFT: nada foi vendido, nada foi prometido — liberdade total.
        if (event.getStatus() == EventStatus.DRAFT){
            return;
        }

        // PUBLISHED: já está à venda. Só campos cosméticos.
        List<String> violations = new ArrayList<>();
        addIfChanged(violations, "name", event.getName(), request.name());
        addIfChanged(violations, "venue", event.getVenue(), request.venue());
        addIfChanged(violations, "city", event.getCity(), request.city());
        addIfChanged(violations, "startsAt", event.getStartsAt(), request.startsAt());
        addIfChanged(violations, "endsAt", event.getEndsAt(), request.endsAt());
        addIfChanged(violations, "ticketPrice", event.getTicketPrice(), request.ticketPrice());

        if (!violations.isEmpty()){
            throw new BusinessRuleException(
                    "Cannot change %s of a PUBLISHED event. Use /reschedule for dates."
                            .formatted(String.join(", ", violations)),
                    "EVENT_FIELD_NOT_EDITABLE");
        }
    }

    /**
     * 🎯 Compara VALOR, não presença.
     *
     * Um PUT reenvia o objeto inteiro, incluindo campos inalterados. Rejeitar
     * "campo presente" quebraria qualquer cliente que faz GET → edita a
     * descrição → PUT. Só reclamamos do que REALMENTE mudou.
     *
     * ⚠️ compareTo para BigDecimal: equals() considera escala, então
     * 100.00 != 100.0 — bug clássico e silencioso em comparação de dinheiro.
     */
    private void addIfChanged(List<String> violations, String field, Object current, Object requested){
        if(requested == null){
            return;   // omitido no payload = "não mexer"
        }
        boolean changed = (current instanceof BigDecimal a
                && requested instanceof BigDecimal b)
                ? a.compareTo(b) != 0
                : !Objects.equals(current, requested);
        if(changed){
            violations.add(field);
        }
    }
}
