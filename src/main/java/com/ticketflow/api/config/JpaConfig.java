package com.ticketflow.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * [SPRING CORE] @Configuration = classe de configuração (pode declarar @Bean).
 * [SPRING DATA] @EnableJpaAuditing ativa o processamento de @CreatedDate e
 * @LastModifiedDate. Sem esta anotação, esses campos ficam NULL e o INSERT
 * estoura no NOT NULL. Erro comum e confuso — anote aí.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
