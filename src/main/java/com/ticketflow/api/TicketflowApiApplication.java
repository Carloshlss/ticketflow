package com.ticketflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * [SPRING BOOT] @SpringBootApplication é um atalho para TRÊS anotações:
 *
 *  1. @Configuration        -> esta classe pode declarar @Bean
 *  2. @EnableAutoConfiguration -> liga a mágica: o Boot varre o classpath
 *                              e configura o que encontrar (Tomcat, Jackson...)
 *  3. @ComponentScan        -> escaneia ESTE pacote e todos os subpacotes
 *                              procurando @Component, @Service, @Repository,
 *                              @RestController para registrar no container.
 *
 * CONSEQUÊNCIA PRÁTICA: tudo que você criar precisa estar dentro de
 * com.ticketflow.api (ou subpacote), senão o Spring não vê.
 */
@SpringBootApplication
public class TicketflowApiApplication {

	public static void main(String[] args) {
		// [SPRING BOOT] Sobe o ApplicationContext (o container de IoC),
		// instancia os beans e inicia o Tomcat embutido na porta configurada.
		SpringApplication.run(TicketflowApiApplication.class, args);
	}

}
