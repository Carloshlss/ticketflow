package com.ticketflow.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * [OPENAPI] Metadados da especificação gerada.
 *
 * Por que isso importa de verdade: o arquivo /v3/api-docs é o CONTRATO
 * legível por máquina da sua API. Na Fase 11 vamos GERAR os clients
 * TypeScript do Angular a partir dele — zero digitação manual de interface,
 * zero divergência entre back e front.
 */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI ticketFlowOpenAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("TicketFlow API")
                        .description("Event ticketing platform - learning project")
                        .version("v1")
                        .contact(new Contact().name("Carlos Henrique")));
    }
}
