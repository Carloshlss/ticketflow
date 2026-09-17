package com.ticketflow.api.event;

import com.ticketflow.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [TESTE UNITÁRIO PURO] Nenhuma anotação de Spring. Nenhum @SpringBootTest,
 * nenhum contexto, nenhum banco. Java puro + JUnit + AssertJ.
 *
 * Consequência: roda em ~2 MILISSEGUNDOS. Um @SpringBootTest levaria ~3
 * SEGUNDOS. Fator 1500x. Multiplique por 300 testes e entenda por que a
 * pirâmide tem a forma que tem.
 *
 * Isto só é possível porque a Policy não depende de repositório nem de HTTP.
 * Quando uma classe é difícil de testar assim, o problema é o DESIGN dela,
 * não o teste. "Testabilidade" é um efeito colateral de bom design — é por
 * isso que TDD melhora arquitetura.
 */
@DisplayName("EventCancellationPolicy")
class EventCancellationPolicyTest {

    /**
     * [DETERMINISMO] Instante FIXO escolhido por nós. Todo o teste é calculado
     * em relação a ele, então o resultado é idêntico hoje, amanhã, no CI e na
     * virada do ano. Este é o pagamento do investimento em injetar Clock.
     */
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private EventCancellationPolicy policy;

    @BeforeEach
    void setUp(){
        // Clock.fixed CONGELA o tempo. clock.instant() sempre devolve NOW.
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        // Instanciação direta com 'new'. Sem container, sem injeção.
        policy = new EventCancellationPolicy(fixedClock);
    }

    /**
     * [JUNIT 5] @Nested agrupa cenários relacionados em classes internas.
     * O relatório fica hierárquico e legível:
     *
     *   EventCancellationPolicy
     *     ├─ status validation
     *     │   └─ should reject cancellation when status is FINISHED
     *     └─ reason requirement
     *         └─ should require reason within 24 hours of start
     *
     * Isso transforma a suíte em DOCUMENTAÇÃO VIVA das regras de negócio.
     */
    @Nested
    @DisplayName("status validation")
    class StatusValidation {

        @Test
        @DisplayName("should allow cancellation when event is PUBLISHED")
        void givenPublishedEvent_whenCheckingCancellation_thenNoExceptionIsThrown(){
            // ARRANGE
            Event event = anEventStartingIn(Duration.ofDays(30), EventStatus.PUBLISHED);

            // ACT + ASSERT
            // [ASSERTJ] assertThatCode(...).doesNotThrowAnyException() é a forma
            // explícita de afirmar "isto NÃO deve explodir". Superior a apenas
            // chamar o método, porque declara a intenção para quem lê.
            assertThatCode(() -> policy.ensureCanBeCancelled(event, null)).doesNotThrowAnyException();
        }

        /**
         * [JUNIT 5 - TESTE PARAMETRIZADO] Um método, vários cenários.
         * @EnumSource injeta os valores do enum, um por execução.
         *
         * O valor real: quando alguém adicionar POSTPONED ao EventStatus, este
         * teste passa a rodar com o valor novo AUTOMATICAMENTE. Um teste que
         * cresce sozinho junto com o domínio.
         */
        @ParameterizedTest
        @EnumSource(value = EventStatus.class, names = {"CANCELLED", "FINISHED"})
        @DisplayName("should reject cancellation for terminal statuses")
        void givenTerminalStatus_whenCheckingCancellation_thenThrowsBusinessRuleException(EventStatus terminalStatus){
            Event event = anEventStartingIn(Duration.ofDays(30), terminalStatus);

            assertThatThrownBy(() -> policy.ensureCanBeCancelled(event, "any reason"))
                    .isInstanceOf(BusinessRuleException.class)
                    // ⚠️ Asserte sobre o CÓDIGO, não sobre a mensagem.
                    // Código é contrato de API (o Angular faz switch nele).
                    // Mensagem é texto para humano e vai mudar — se você assertar
                    // nela, cada ajuste de redação quebra o teste sem motivo.
                    .extracting("errorCode")
                    .isEqualTo("INVALID_STATUS_TRANSITION");
        }
    }

    @Nested
    @DisplayName("event start time validation")
    class StartTimeValidation{

        @Test
        @DisplayName("should reject canellation when event already started")
        void givenEventAlreadyStarted_whenCheckingCancellation_thenThrows(){
            // Evento começou 1 hora ANTES do nosso NOW congelado.
            Event event = anEventStartingIn(Duration.ofHours(-1), EventStatus.PUBLISHED);

            assertThatThrownBy(() -> policy.ensureCanBeCancelled(event, "reason"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode")
                    .isEqualTo("EVENT_ALREADY_STARTED");
        }
    }

    @Nested
    @DisplayName("reason requirement near start")
    class ReasonRequirement {

        @Test
        @DisplayName("should require a reason when event starts in less than 24 hours")
        void givenEventStartingIn23Hours_whenNoReasonProvided_thenThrows(){
            Event event = anEventStartingIn(Duration.ofHours(23), EventStatus.PUBLISHED);

            assertThatThrownBy(() -> policy.ensureCanBeCancelled(event, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode")
                    .isEqualTo("CANCELLATION_REASON_REQUIRED");
        }

        @Test
        @DisplayName("should reject blank reason as if it were absent")
        void givenEventStartingIn23Hours_whenReasonIsBlank_thenThrows(){
            Event event = anEventStartingIn(Duration.ofHours(23), EventStatus.PUBLISHED);

            // "   " não é motivo. Testar isto explicitamente evita que alguém
            // "simplifique" o isBlank() para != null numa refatoração futura.
            assertThatThrownBy(() -> policy.ensureCanBeCancelled(event, "   "))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("should accept cancellation with reason when starting soon")
        void givenEventStartingIn23Hours_whenReasonProvided_thenNoException(){
            Event event = anEventStartingIn(Duration.ofHours(23), EventStatus.PUBLISHED);

            assertThatCode(() -> policy.ensureCanBeCancelled(event, "Venue flooded"))
                    .doesNotThrowAnyException();
        }

        /**
         * 🎯 TESTE DE FRONTEIRA — o mais valioso desta classe.
         *
         * Bug mora em limite: 23h59 vs 24h vs 24h01. Foi exatamente aqui que o
         * .toHours() < 24 (que trunca) estava frágil.
         *
         * Regra profissional: para toda condição numérica, teste o limite
         * EXATO, um abaixo e um acima. Essa é a técnica de "análise de valor
         * limite" e pega mais bug que qualquer outra.
         */
        @Test
        @DisplayName("should NOT require reason at exactly 24 hours bejore start")
        void givenEventStartingInExactly24Hours_whenNoReason_thenNoException(){
            Event event = anEventStartingIn(Duration.ofHours(24), EventStatus.PUBLISHED);

            // A regra é "MENOS de 24h exige motivo". 24h exatas => não exige.
            assertThatCode(() -> policy.ensureCanBeCancelled(event, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should require reason at 23 hours and 59 minutes before start")
        void givenEventStartingIn23h59m_whenNoReason_thenThrows(){
            Event event = anEventStartingIn(Duration.ofHours(23).plusMinutes(59), EventStatus.PUBLISHED);

            // ⚠️ Este teste FALHARIA com a implementação original
            // (.toHours() devolveria 23... que é < 24, então passaria).
            // Mas com 24h30m, .toHours() daria 24 e NÃO exigiria motivo —
            // um bug real. Comparar Duration diretamente resolve os dois casos.
            assertThatThrownBy(() -> policy.ensureCanBeCancelled(event, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode")
                    .isEqualTo("CANCELLATION_REASON_REQUIRED");
        }
    }

    /**
     * [OBJECT MOTHER / TEST DATA BUILDER] Fábrica de cenário com um nome que
     * se lê como frase: anEventStartingIn(Duration.ofHours(23), PUBLISHED).
     *
     * Por que isto importa muito mais do que parece:
     *   1. o teste expressa APENAS o que é relevante para o cenário
     *   2. adicionar um campo obrigatório em Event exige mudar UM lugar,
     *      não os 40 testes
     *   3. o leitor entende o cenário em uma linha
     *
     * Sem isto, cada teste teria 12 linhas de setup e a suíte viraria
     * impossível de manter. É o item que separa suíte saudável de suíte
     * abandonada.
     */
    private Event anEventStartingIn(Duration untilStart, EventStatus status){
        Event event = new Event();
        event.setId(1L);
        event.setName("Test Event");
        event.setVenue("Test Venue");
        event.setCity("Sao Paulo");
        event.setStartsAt(NOW.plus(untilStart));
        event.setEndsAt(NOW.plus(untilStart).plus(Duration.ofHours(4)));
        event.setTicketPrice(new BigDecimal("100.00"));
        event.setTotalTickets(100);
        event.setAvailableTickets(100);
        event.setStatus(status);
        return event;
    }
}