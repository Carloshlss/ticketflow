package com.ticketflow.api.config;

import com.ticketflow.api.domain.event.Event;
import com.ticketflow.api.domain.event.EventRepository;
import com.ticketflow.api.domain.event.EventStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * [SPRING BOOT] ApplicationRunner executa código após o contexto subir.
 * [SPRING CORE] @Profile("lab") — só roda no perfil "lab". Perfis isolam
 * comportamento por ambiente (dev/test/prod). Ative com:
 *   spring.profiles.active=lab
 */
/**
 * ⚠️ CLASSE DE ESTUDO / LABORATÓRIO — não faz parte do fluxo da aplicação.
 *
 * [SPRING CORE] @Profile("lab"): este bean só é registrado no contexto quando
 * o perfil "lab" estiver ativo. Com o perfil desligado, a classe nem é
 * instanciada — custo zero em runtime, código preservado para consulta.
 *
 * Como executar quando quiser estudar:
 *   - IntelliJ: Run Configuration -> Active profiles: dev,lab
 *   - Terminal: mvn spring-boot:run -Dspring-boot.run.profiles=dev,lab
 *   - Jar:      java -jar app.jar --spring.profiles.active=dev,lab
 *
 * Substitui deprecar/comentar: a classe compila, a IDE ainda navega nela,
 * mas não interfere na aplicação.
 */
@Slf4j                    // [LOMBOK] gera: private static final Logger log = ...
@Configuration
@Profile("lab")
@RequiredArgsConstructor  // [LOMBOK] construtor com os campos final => injeção por construtor
public class EventDataExplorer {

    // [SPRING CORE] INJEÇÃO POR CONSTRUTOR (via @RequiredArgsConstructor).
    // Prefira sempre a @Autowired em campo, porque:
    //   1. o campo pode ser final => imutável
    //   2. dependência obrigatória fica explícita na assinatura
    //   3. testável sem container: new EventDataExplorer(mockRepo)
    // Desde o Spring 4.3, um único construtor não precisa de @Autowired.
    private final EventRepository eventRepository;

    private final EntityManager entityManager;

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
                    .organizerName("King Events")
                    .build());
            log.info("Saved id={} createdAt={} version={}",
                    created.getId(), created.getCreatedAt(), created.getVersion());

            log.info("=== 6. Método de negócio + lock otimista ===");
            created.reserveTickets(10);           // altera o objeto em memória
            Event updated = eventRepository.save(created);
            log.info("available={} version={} (version incrementou!)",
                    updated.getAvailableTickets(), updated.getVersion());
//            Long EventId = updated.getId();

//            NÃO ESTÁ CERTO!!
//            log.info("=== 7. Prova de concorrência (Lock Otimista) ===");
//
//            // Simula o Usuário A buscando o evento
//            Event userA = eventRepository.findById(EventId).orElseThrow(() -> new RuntimeException("Event not found!"));
//            // Desvincula o Usuário A do cache em memória para forçar uma nova busca real no próximo findById
//            entityManager.detach(userA);
//
//            // Simula o Usuário B buscando o MESMO evento (com o mesmo número de versão do banco)
//            Event userB = eventRepository.findById(EventId).orElseThrow(() -> new RuntimeException("Event not found!"));
//
//            // Usuário A faz uma reserva e salva primeiro
//            log.info("[User A] Reservando 5 ingressos...");
//            userA.reserveTickets(5);
//            eventRepository.save(userA); // Sucesso! A versão no banco vai incrementar
//            log.info("[User A] Salvo com sucesso. Versão no banco incrementada.");
//
//            // Usuário B tenta salvar sua alteração baseada na versão desatualizada que ele leu
//            try{
//                log.info("[User B] Tentando reservar 2 ingressos ao mesmo tempo...");
//                userB.reserveTickets(2);
//                eventRepository.save(userB); // O Hibernate vai interceptar e lançar o erro aqui!
//            } catch(ObjectOptimisticLockingFailureException e){
//                log.error("TEST SUCCESSFULL! Lock Otimista bloqueou a alteração concorrente.");
//                log.error("Exceção capturada com sucesso: {}", e.getClass().getSimpleName());
//                log.warn("Error message: {}", e.getMessage());
//            }

            log.info("=== 8. testando o findByTicketPriceBetweenAndCityIgnoreCase no repositório ===");
            eventRepository.findByTicketPriceBetweenAndCityIgnoreCase(
                    new BigDecimal("50.00"), new BigDecimal("350.00"), "São Paulo")
                    .forEach(e -> log.info("{} | {} | {} | {}",
                            e.getId(), e.getName(), e.getTicketPrice(), e.getCity()));
        };
    }
}
