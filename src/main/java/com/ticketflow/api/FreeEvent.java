package com.ticketflow.api;

import com.ticketflow.api.event.Event;
import com.ticketflow.api.shared.exception.BusinessRuleException;

import java.math.BigDecimal;

public class FreeEvent extends Event {
    @Override
    public void setTicketPrice(BigDecimal ticketPrice){
        if(ticketPrice != null && ticketPrice.compareTo(BigDecimal.ZERO) > 0){
            throw new BusinessRuleException("YOU CAN'T", "CANT_DO");
        }
        super.setTicketPrice(BigDecimal.ZERO);
    }
}
