package com.ticketflow.api;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * [TESTCONTAINERS] Classe BASE para todos os testes de integração.
 *
 * ⚡ O DETALHE MAIS IMPORTANTE DESTA CLASSE: 'static'.
 *
 * Um @Container static é iniciado UMA vez por JVM e compartilhado por todas
 * as classes de teste que herdam desta. Se fosse não-static, o container
 * subiria e desceria a cada CLASSE de teste — 8 segundos vezes 20 classes.
 *
 * Isso funciona junto com o CACHE DE CONTEXTO do Spring: o Spring Test
 * mantém em cache os ApplicationContexts por CONFIGURAÇÃO. Como todos os
 * testes que herdam daqui têm configuração idêntica, o contexto sobe UMA vez.
 *
 * ⚠️ Corolário prático crucial: cada combinação diferente de anotações
 * (@ActiveProfiles diferente, @MockitoBean diferente, @TestPropertySource)
 * cria um contexto NOVO. Suíte com 15 contextos distintos leva minutos a mais.
 * PADRONIZE a configuração dos testes de integração — é a otimização de
 * suíte com melhor retorno que existe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
public abstract class AbstractIntegrationTest {

	/**
	 * [TESTCONTAINERS] Postgres 17-alpine — a MESMA imagem do compose.yaml.
	 * Paridade dev/test/prod. Fixe a tag; 'latest' quebra build sem aviso.
	 */
	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer("postgres:17-alpine")
					.withDatabaseName("ticketflow_test")
					.withUsername("test")
					.withPassword("test")
					// [PERFORMANCE] Só para teste: desliga a durabilidade do
					// Postgres. fsync=off deixa os testes ~3x mais rápidos.
					// Em produção isto seria um crime — dados perdidos em queda.
					.withCommand("postgres", "-c", "fsync=off",
							"-c", "full_page_writes=off",
							"-c", "synchronous_commit=off")
					// Reutiliza o container entre execuções (precisa de
					// testcontainers.reuse.enable=true no ~/.testcontainers.properties)
					.withReuse(true);

	/**
	 * 🎁 [SPRING BOOT 3.1+] @ServiceConnection é a peça que faz isto ser
	 * elegante. Ele detecta o tipo do container e configura AUTOMATICAMENTE
	 * spring.datasource.url / username / password.
	 *
	 * [BOOT 3 vs 4] Antes disso, era necessário um @DynamicPropertySource:
	 *
	 *   @DynamicPropertySource
	 *   static void props(DynamicPropertyRegistry registry) {
	 *       registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
	 *       registry.add("spring.datasource.username", POSTGRES::getUsername);
	 *       registry.add("spring.datasource.password", POSTGRES::getPassword);
	 *   }
	 *
	 * Você vai ver essa forma em todo tutorial. Ela funciona, mas
	 * @ServiceConnection é o caminho atual e funciona com Kafka, Redis e
	 * Mongo também — vamos reaproveitar esta classe nas Fases 8, 9 e 10.
	 */
}
