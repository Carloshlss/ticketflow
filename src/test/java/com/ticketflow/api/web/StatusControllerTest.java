package com.ticketflow.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [TESTE DE INTEGRAÇÃO - FATIADO] @WebMvcTest sobe APENAS a camada web
 * (controllers, filtros, conversores JSON). Não sobe banco nem serviços.
 * É rápido e testa o "contrato HTTP": rota, status, formato do JSON.
 *
 * Compare com @SpringBootTest, que sobe a aplicação inteira (Fase 5).
 */
@WebMvcTest(StatusController.class)
class StatusControllerTest {

    // [SPRING] Injeção de dependência: o Spring entrega o MockMvc pronto.
    // MockMvc simula requisições HTTP SEM abrir porta de rede real.
    @Autowired
    private MockMvc mockMvc;

    @Test
    void givenApplicationIsRunning_whenHealthCheckIsCalled_thenStatusShouldBeUp() throws Exception{
        mockMvc.perform(get("/api/v1/status"))          // ARRANGE + ACT
                .andExpect(status().isOk())                         // ASSERT: 200
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.apiVersion").value("v1"));
    }

    @Test
    void givenMessageInUrl_whenEndpointIsCalled_thenEchoReceivedMessage() throws Exception{
        mockMvc.perform(get("/api/v1/status/echo/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("hello"));
    }

    @Test
    void givenNonExistentRoute_whenEndpointIsCalled_thenReturns404NotFound() throws Exception{
        mockMvc.perform(get("/api/v1/notFound"))
                .andExpect(status().isNotFound());
    }
}