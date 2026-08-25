package com.ticketflow.api.config;

import com.ticketflow.api.domain.event.Event;
import com.ticketflow.api.domain.event.EventRepository;
import com.ticketflow.api.domain.event.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * [SPRING BOOT] ApplicationRunner executa código após o contexto subir.
 * [SPRING CORE] @Profile("dev") — só roda no perfil "dev". Perfis isolam
 * comportamento por ambiente (dev/test/prod). Ative com:
 *   spring.profiles.active=dev
 *
 * ⚠️ ARQUIVO TEMPORÁRIO — apagamos quando existir o controller.
 */
@Slf4j                    // [LOMBOK] gera: private static final Logger log = ...
@Configuration
@Profile("dev")
@RequiredArgsConstructor  // [LOMBOK] construtor com os campos final => injeção por construtor
public class EventDataExplorer {

    // [SPRING CORE] INJEÇÃO POR CONSTRUTOR (via @RequiredArgsConstructor).
    // Prefira sempre a @Autowired em campo, porque:
    //   1. o campo pode ser final => imutável
    //   2. dependência obrigatória fica explícita na assinatura
    //   3. testável sem container: new EventDataExplorer(mockRepo)
    // Desde o Spring 4.3, um único construtor não precisa de @Autowired.
    private final EventRepository eventRepository;

    @Bean
    ApplicationRunner exploreEventData(){
        return args -> {
            log.info("=== 1. findAll() - vieram do Flyway V2 ===");
            eventRepository.findAll()
                    .forEach(e -> log.info("{} | {} | {} | {}",
                            e.getId(), e.getName(), e.getCity(), e.getStatus()));

            log.info("=== 2. Query derivada do nome do método ===");
            log.info("PUBLISHED: {}", eventRepository.findByStatus(EventStatus.PUBLISHED).size());

            log.info("=== 3. @Query com JPQL ===");
            eventRepository.findAvailableEvents(EventStatus.PUBLISHED, Instant.now())
                    .forEach(e -> log.info("Available: {}", e.getName()));

            log.info("=== 4. Native query com projeção ===");
            eventRepository.countPublishedEventsByCity()
                    .forEach(row -> log.info("{} -> {}", row[0], row[1]));

            log.info("=== 5. INSERT: observe o SQL e os campos de auditoria ===");
            Event created = eventRepository.save(Event.builder()
                    .name("Docker Workshop " + Instant.now().toEpochMilli())
                    .description("Hands-on containers")
                    .venue("Tech Hub")
                    .city("Campinas")
                    .startsAt(Instant.now().plus(30, ChronoUnit.DAYS))
                    .endsAt(Instant.now().plus(30, ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS))
                    .ticketPrice(new BigDecimal(150.00))
                    .totalTickets(50)
                    .availableTickets(50)
                    .status(EventStatus.PUBLISHED)
                    .build());
            log.info("Saved id={} createdAt={} version={}",
                    created.getId(), created.getCreatedAt(), created.getVersion());

            log.info("=== 6. Método de negócio + lock otimista ===");
            created.reserveTickets(10);           // altera o objeto em memória
            Event updated = eventRepository.save(created);
            log.info("available={} version={} (version incrementou!)",
                    updated.getAvailableTickets(), updated.getVersion());
        };
    }
}
