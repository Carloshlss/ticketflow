package com.ticketflow.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * [TESTABILIDADE] Expor o "relógio" como um BEAN é uma das técnicas mais
 * valiosas e menos conhecidas do desenvolvimento profissional.
 *
 * O problema com Instant.now() dentro da regra de negócio: o tempo é uma
 * DEPENDÊNCIA OCULTA e incontrolável. Para testar "faltam 23 horas para o
 * evento", você teria que criar dados relativos a agora e torcer para o teste
 * não rodar exatamente na virada de um limite. Testes assim são "flaky":
 * passam 99 vezes e falham na centésima, no pipeline, sem ninguém entender.
 *
 * Com Clock injetado, o teste usa Clock.fixed(...) e o tempo fica CONGELADO
 * num instante escolhido. O teste passa a ser 100% determinístico.
 *
 * [DIP] Tempo deixou de ser um detalhe global (System.currentTimeMillis) e
 * passou a ser uma abstração injetada. Mesmo princípio do banco de dados.
 */
@Configuration
public class TimeConfig {
    @Bean
    public Clock systemClock(){
        // UTC para casar com Instant/TIMESTAMPTZ e não depender do fuso do servidor.
        return Clock.systemUTC();
    }
}
