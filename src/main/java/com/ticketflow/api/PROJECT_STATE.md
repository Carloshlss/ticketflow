# TicketFlow — Project State

> Documento de referência do estado atual do projeto.
> **Atualizar ao final de cada fase.** Colar no início de cada nova fase.

**Última atualização:** Fase 5 (testes) — 10/09/2026

---

## 1. Stack e versões

| Item | Versão | Observação |
|---|---|---|
| Java | 21 (LTS) | |
| Spring Boot | **4.1.1** | ⚠️ Framework 7, Jackson 3, Hibernate 7.1, JUnit 6, Security 7 |
| Build | Maven | |
| Banco | PostgreSQL 17-alpine | Docker, porta host **5433** |
| Migrations | Flyway | |
| Testes | JUnit 6 + AssertJ + Mockito + Testcontainers 2.x | |
| Docs | springdoc-openapi 3.1.0 | |
| Arquitetura | Pacotes por feature | hexagonal prevista na Fase 9.5 |

### ⚠️ Diferenças Boot 4 vs Boot 3 (tutoriais estão desatualizados)

| Boot 3 | Boot 4                                              |
|---|-----------------------------------------------------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc`                        |
| `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean`                  |
| `...test.autoconfigure.web.servlet.WebMvcTest` | `...boot.webmvc.test.autoconfigure.WebMvcTest`      |
| `...boot.test.web.client.TestRestTemplate` | `...boot.resttestclient.TestRestTemplate`           |
| `org.testcontainers.containers.PostgreSQLContainer` | `org.testcontainers.postgresql.PostgreSQLContainer` |
| artefato `postgresql` | artefato `testcontainers-postgresql`                |
| `spring-boot-test-autoconfigure` monolítico | um `*-test` companion por starter                   |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.json.JsonMapper`            |
| `org.testcontainers.containers.PostgreSQLContainer` | `org.testcontainers.postgresql.PostgreSQLContainer` |
| `TestRestTemplate` | `RestTestClient` + `@AutoConfigureRestTestClient`     |
| `MockitoTestExecutionListener` (auto) | removido - `@Mock` em `@SpringBootTest` fica `null`              |


---

## 2. Convenções (não negociáveis)

- **Idioma:** tudo em **inglês** — código, comentários, commits, nomes de teste, mensagens de erro. Nenhum português no repositório.
- **Nome de teste:** `given<Contexto>_when<Ação>_then<ResultadoEsperado>`
    - o `then` descreve o **comportamento de negócio**, não o tipo da exception
    - ✅ `thenCancellationIsRejected` · ❌ `thenThrowsBusinessRuleException`
- **`@DisplayName`** em toda classe e método de teste, em inglês legível
- **Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`)
- **Branch por fase:** `feat/fase-NN-descricao` → PR → auto-review do diff → merge
- **Dados:** cidades **sem acento** (`Sao Paulo`), consistente com migrations e seeds
- **Datas:** `Instant` (UTC) na entidade, `TIMESTAMPTZ` no banco. Nunca `Date`/`Calendar`
- **Dinheiro:** `BigDecimal` + `NUMERIC(12,2)`. Nunca `double`
- **Tempo em teste:** `Clock` injetado + `Clock.fixed`. **Nunca `Instant.now()`** em teste
- **Config:** apenas `application.yml`. Nunca `.properties` (precedência conflitante)
- **Nome de variável carrega unidade:** `uptimeInSeconds`, não `uptime`
- **`ApiError`:** o campo do código de erro no JSON é **`code`** (não `errorCode`). No Java, a exception expõe `errorCode`. Não confundir jsonPath com campo Java.
- **Id em teste de integração:** nunca `setId()`. Com `@Version`, id != null + version == null = entidade *detached* → Hibernate recusa o persist.
- **Paginação:** `max-page-size: 100`. O Spring **trunca**, não rejeita.
- - **`ApiError` — nomes reais dos campos JSON:** `timestamp`, `status`, `error` (reason phrase HTTP, String), **`code`** (código  de negócio), `message`, `path`, **`fieldErrors`** (array de validação).⚠️ `$.error` NÃO é o código nem um array. No Java a exception expõe `errorCode`; no JSON a chave é `code`. Não confundir.
- **`src/test/resources/application.yml` SUBSTITUI o de `main`**, não
  complementa. Toda propriedade que o teste precisa deve estar repetida lá.
- **Nunca asserte host/porta:** use `endsWith(...)` ou `redirectedUrlPattern`. `RANDOM_PORT` muda a porta a cada execução.
- **Paginação:** o Spring **trunca** (clamp) para `max-page-size`, não rejeita. Default do Spring Data é 2000 — se o teste vê 2000, a propriedade não foi lida.
- **`@JsonIgnore` em todo método `@AssertTrue`** de DTO: o Jackson serializa `isX()` como propriedade e vaza o campo de validação no payload.
- **Nunca conte erros de validação** (`length() == 3`): asserte QUAIS campos  falharam com `$.fieldErrors[*].field` + `hasItems(...)`.
- **Stub obrigatório em `@WebMvcTest`:** mock não stubado retorna `null` e o corpo da resposta vem vazio, mascarando o que o teste deveria verificar.


---

## 3. Estrutura de pacotes

```
com.ticketflow.api
├── TicketflowApiApplication
├── event/
│   ├── Event, EventCancellationPolicy, EventCommandService
│   ├── EventController, EventMapper, EventPolicy, 
│   ├── EventQueryService, EventRepository, EventReschedulePolicy
│   ├── EventSpecification, EventStatus,
│   ├── EventUniquenessChecker, EventUpdatePolicy
│   ├── dto/        → CancelEventRequest, CityEventCount, CreateEventRequest, EventFilter,
│   │                 EventResponse, EventSummaryResponse, RescheduleEventRequest, UpdateEventRequest
│   ├── port/       → EventNotificationPort
│   └── exception/  → InvalidEventStatusTransitionException
├── notification/   → LoggingNotificationAdapter        (depende de event)
├── pricing/        → BulkDiscountPolicy, ConvernienceFeePolicy, HalfPricePolicy,
│                     PriceCalculator, PricingContext, PricingPolicy
├── shared/
│   ├── dto/        → PagedResponse, ApiError
│   ├── exception/  → BusinessRuleException, ResourceNotFoundException,
│   │                 DuplicateResourceException, GlobalExceptionHandler
│   └── filter/     → CorrelationIdFilter
├── validation/     → EventIntervalAware, ValidEventDuration, ValidEventDurationValidator
└── config/         → EventDataExplorer, JpaConfig, OpenApiConfig, TimeConfig
```

**Regras de dependência (validadas por ArchUnit):**
- `shared` não conhece nenhum domínio — deve ser copiável para outro projeto
- portas ficam no módulo que **consome**, não no que implementa
- sem ciclos entre pacotes de primeiro nível

---

## 4. Endpoints

| Verbo | Rota | Status |
|---|---|---|
| GET | `/api/v1/status`, `/status/echo/{message}`, `/status/uptime` | ✅ |
| GET | `/api/v1/events` (paginado) | ✅ |
| GET | `/api/v1/events/{id}` | ✅ |
| GET | `/api/v1/events/search` (Specification) | ✅ |
| GET | `/api/v1/events/showcase` | ✅ |
| POST | `/api/v1/events` → 201 + Location | ✅ |
| PUT | `/api/v1/events/{id}` | ✅ |
| POST | `/api/v1/events/{id}/publish` | ✅ |
| POST | `/api/v1/events/{id}/cancel` | ✅ |
| PATCH | `/api/v1/events/{id}/reschedule` | ✅ Fase 5 (TDD) |
| DELETE | `/api/v1/events/{id}` | ✅ |

---

## 5. Migrations aplicadas

| Versão | Descrição                                            |
|--------|------------------------------------------------------|
| V1     | `create_event_table` (+ índices e CHECK constraints) |
| V2     | `insert_seed_events`                                 |
| V3     | `add_organizer_name_column_in_event_table`           |
| V4     | `add_unique_constraint_event_name`                   |
| V5+    | *(atualizar conforme criadas)*                       |

---

## 6. Testes — estado

| Classe | Tipo                 | Status |
|---|----------------------|---|
| `StatusControllerTest` | `@WebMvcTest`        | ✅ |
| `ArchitectureTest` | ArchUnit (8 regras)  | ✅ |
| `EventCancellationPolicyTest` | unitário puro        | ✅ |
| `EventReschedulePolicyTest` | unitário puro (TDD)  | ✅ |
| `EventCommandServiceTest` | Mockito              | ✅ |
| `EventControllerTest` | `@WebMvcTest`        | ✅ |
| `AbstractIntegrationTest` | base Testcontainers  | ✅ |
| `EventRepositoryIntegrationTest` | integração           | ✅ |
| `EventApiIntegrationTest` | integração HTTP      | ✅ |
| `EventQueryPerformanceTest` | orçamento de queries | ✅ |
| `EventUpdatePolicyTest ` | unitário puro        | ✅ |

**Gates:** JaCoCo BRANCH ≥ 70% · PIT em `*Policy` e `pricing`

**Orçamento de queries (linha de base):** `/api/v1/events/search` = 2 queries (dados + count)

---

## 7. 🔜 Decisões diferidas (ADRs pendentes)

| Item | Fase        | Motivo de postergar |
|---|-------------|---|
| Split `DiscountPolicy` / `FeePolicy`, `PriceBreakdown`, "melhor desconto", `PricingProperties`, tabela `discount_rule` | **8**       | breakdown só é requisito quando existe `Order`; também trata corrida de `max_uses` |
| `LoggingNotificationAdapter` → `@ConditionalOnProperty` + adapter Kafka real + `@TransactionalEventListener(AFTER_COMMIT)` | **9**       | escolha por propriedade só tem função com adapter real |
| Domain events em vez de passar `Event` para a porta de notificação | **9**       | payload precisa ser serializável de qualquer forma |
| Migração para arquitetura hexagonal | **9.5**     | requer suíte de testes verde como rede de segurança |
| `WebTestClient` / `RestTestClient` em vez de `TestRestTemplate` | **decidir** | *(pendente de resposta)* |
| PIT / mutation testing | **13**      | `pitest-junit5-plugin` ainda não suporta JUnit 6 |
| i18n de mensagens de validação (`messages.properties`) | **6**       | hoje o cliente recebe a chave literal (`event.organizer.required`) em vez de texto legível |

---

## 8. Decisões tomadas

| Decisão | Razão |
|---|---|
| Testcontainers, **não H2** | H2 divergiria em `TIMESTAMPTZ`, `NUMERIC`, `JSONB`, locks; o `@Version` da Fase 8 seria intestável |
| `open-in-view: false` | evita "Open Session in View", que esconde N+1 e prende conexão |
| `ddl-auto: validate` | Flyway é o dono do schema; validate é rede de proteção no startup |
| `@Enumerated(STRING)` | ORDINAL corrompe dados históricos se o enum for reordenado |
| `Clock` injetado | determinismo em teste; permite `Clock.fixed` |
| Cobertura por **BRANCH**, não LINE | LINE conta um `if` como coberto tendo avaliado só um lado |
| Porta no módulo consumidor | DIP de verdade; elimina ciclo `event ↔ notification` |
| YAML, nunca `.properties` | ter os dois faz o `.properties` sobrescrever silenciosamente |
| Nunca `Instant.now()` em regra de negócio | dependência oculta, impossível de testar; passe `Instant` now como parâmetro |
| Porta com `@ConditionalOnProperty(matchIfMissing=true)`, nunca `@Profile` | `@Profile` permite ZERO implementações e a app não sobe |
| Construção manual do service no teste, não `@InjectMocks` | `@InjectMocks` falha silenciosamente ao adicionar parâmetro no construtor |
| Datas de teste relativas ao agora | data absoluta no futuro é bomba de tempo |
| PIT postergado para a Fase 13 | `pitest-junit5-plugin` ainda não suporta JUnit 6 |
| Edição por campo, não por status | renomear/mover evento vendido invalida o ingresso já comprado |
| `BigDecimal.compareTo`, nunca `equals` | `equals` considera escala: `100.00 != 100.0` |
| `@{argLine}` obrigatório no surefire | omitir zera a cobertura do JaCoCo silenciosamente |
| Policy real (não mock) em teste de service | mock de lógica pura faz o teste passar sem testar a regra |
| `Location` absoluto (default do Spring) | permitido pela RFC 9110; o teste se adapta, não o código |
| `mockito-core` declarado explicitamente | necessário para `${org.mockito:mockito-core:jar}` resolver no `-javaagent` |
| Payload válido = serializar objeto; payload inválido = JSON cru | text block não é validado pelo compilador — 3 testes falharam por typo |

---

## 9. Comandos frequentes

```bash
docker compose up -d              # sobe o Postgres
docker compose ps                 # confere healthy
docker compose down               # para, mantém volume
docker compose down -v            # ⚠️ APAGA o banco

mvn spring-boot:run               # sobe a app (8080)
mvn test                          # testes
mvn verify                        # testes + JaCoCo report + gate
mvn test -Dtest=NomeDoTeste       # um teste só
mvn test-compile org.pitest:pitest-maven:mutationCoverage   # mutation testing

# Relatórios
target/site/jacoco/index.html
target/pit-reports/index.html

# Swagger
http://localhost:8080/swagger-ui.html
```

---

## 10. Roteiro

| Fase | Tema | Status |
|---|---|---|
| 1 | Fundação, Git, primeiro endpoint | ✅ |
| 2 | JPA, Postgres, Flyway | ✅ |
| 3 | DTOs, validação, `@ControllerAdvice`, paginação, Swagger | ✅ |
| 4 | SOLID, refatoração, ArchUnit | ✅ |
| 5 | Testes, Testcontainers, TDD, JaCoCo, PIT | 🔄 |
| 6 | Spring Security 7 (stateful) | ⬜ |
| 7 | JWT, roles, CORS | ⬜ |
| 8 | Compra, idempotência, Redis, lock otimista | ⬜ |
| 9 | Kafka, outbox, retry/DLQ | ⬜ |
| 9.5 | Arquitetura hexagonal | ⬜ |
| 10 | MongoDB, auditoria, CQRS | ⬜ |
| 11 | Angular, Bootstrap, DOM, forms reativos | ⬜ |
| 12 | JWT no front, guards, Cypress E2E | ⬜ |
| 13 | Dockerfile multi-stage, GitHub Actions | ⬜ |
| 14 | Deploy cloud, Prometheus, Grafana | ⬜ |
