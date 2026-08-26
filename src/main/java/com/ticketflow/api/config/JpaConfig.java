package com.ticketflow.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * [SPRING CORE] @Configuration: classe que DECLARA beans. É processada no
 * startup, antes dos beans de negócio existirem.
 *
 * [SPRING DATA] @EnableJpaAuditing: importa AuditingHandler +
 * AuditingEntityListener no contexto. Sem isso, @CreatedDate/@LastModifiedDate
 * são ignorados silenciosamente e o INSERT quebra no NOT NULL.
 *
 * Os atributos abaixo apontam para os beans que o handler vai consultar.
 */
@Configuration
@EnableJpaAuditing(
        dateTimeProviderRef = "utcDateTimeProvider",
        auditorAwareRef = "auditorProvider"
)
public class JpaConfig {

    /**
     * [SPRING CORE] @Bean: o MÉTODO produz um objeto gerenciado pelo container.
     * O nome do bean é o nome do método ("utcDateTimeProvider") — é exatamente
     * a string referenciada acima em dateTimeProviderRef.
     *
     * Por padrão o Spring Data usa o relógio do sistema no fuso local.
     * Forçamos UTC para casar com o TIMESTAMPTZ e com o Instant da entidade.
     */
    @Bean
    public DateTimeProvider utcDateTimeProvider(){
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }


    /**
     * [SPRING DATA] AuditorAware responde "quem está fazendo esta operação?".
     * É o que alimenta @CreatedBy / @LastModifiedBy.
     *
     * Hoje devolve um valor fixo, porque ainda não temos autenticação.
     * Na FASE 7 isto vira:
     *   SecurityContextHolder.getContext().getAuthentication().getName()
     * e aí toda entidade passa a registrar automaticamente quem criou e
     * quem alterou — auditoria de verdade, sem uma linha nos services.
     */
    @Bean
    public AuditorAware<String> auditorProvider(){
        return () -> Optional.of("system");
    }
}
