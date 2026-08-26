package com.ticketflow.api.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * [SPRING DATA JPA] A parte mais "mágica" e a que você precisa entender melhor.
 *
 * Isto é uma INTERFACE sem implementação. Em tempo de execução o Spring Data
 * cria um PROXY dinâmico que implementa cada método:
 *   - herdados de JpaRepository -> delegam para SimpleJpaRepository (usa EntityManager)
 *   - derivados do nome         -> o nome é PARSEADO e vira JPQL
 *   - anotados com @Query       -> usam o JPQL/SQL que você escreveu
 *
 * @Repository é opcional aqui (o Spring Data já registra o bean), mas
 * documenta a intenção e habilita a tradução de exceções do JDBC para
 * a hierarquia DataAccessException do Spring.
 *
 * <Event, Long> = <tipo da entidade, tipo do id>
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Herdado de graça: save, saveAll, findById, findAll, findAll(Pageable),
    // count, existsById, delete, deleteById, flush...

    /**
     * [SPRING DATA - QUERY METHOD] Query derivada do nome do método.
     * Gramática: find|read|get|count|exists + By + Propriedade + Operador
     *
     * Isto gera: SELECT e FROM Event e WHERE e.status = ?1
     * Optional<T> (Java 8) é o retorno correto para "0 ou 1" — sem risco de NPE.
     */
    List<Event> findByStatus(EventStatus status);

    /**
     * Combinando: And, IgnoreCase, OrderBy, e paginação.
     * Gera: WHERE lower(e.city) = lower(?1) AND e.status = ?2 ORDER BY e.startsAt ASC
     *
     * [API REST] Page<T> traz total de elementos, total de páginas e conteúdo.
     * Fase 3 expõe isso no endpoint.
     */
    Page<Event> findByCityIgnoreCaseAndStatusOrderByStartsAtAsc(String city, EventStatus status, Pageable pageable);

    /** Operadores de comparação: After, Before, Between, GreaterThan, Containing... */
    List<Event> findByStartsAtAfterAndStatus(Instant reference, EventStatus status);

    Optional<Event> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * [SPRING DATA - @Query com JPQL]
     * Quando o nome do método derivado ficaria absurdo, escreva a query.
     *
     * ⚠️ JPQL ≠ SQL: opera sobre ENTIDADES e seus atributos Java
     * ("FROM Event e" = a classe; "e.startsAt" = o campo), não sobre tabelas
     * e colunas. O Hibernate traduz para o SQL do dialeto do banco.
     *
     * :param nomeado + @Param = legível e imune a SQL injection
     * (o Hibernate usa PreparedStatement com bind de parâmetros).
     */
    @Query("SELECT e FROM Event e " +
            "WHERE e.status = :status " +
            "AND e.availableTickets > 0 " +
            "AND e.startsAt >= :from " +
            "ORDER BY e.startsAt ASC")
    List<Event> findAvailableEvents(@Param("status") EventStatus status, @Param("from") Instant from);

    /**
     * [SPRING DATA] nativeQuery = true: SQL puro do Postgres.
     * Use quando precisar de recurso específico do banco (window functions,
     * CTE, JSONB). Custo: perde portabilidade e usa nomes de TABELA/COLUNA.
     */
    @Query(value = """
                SELECT city, COUNT(*) AS total
                FROM event
                WHERE status = 'PUBLISHED'
                GROUP BY city
                ORDER BY total DESC
                """, nativeQuery = true)    // [JAVA 15+] Text Block: string multilinha legível. Não existe no Java 8!
    List<Object[]> countPublishedEventsByCity();

    List<Event> findByTicketPriceBetweenAndCityIgnoreCase(BigDecimal min, BigDecimal max, String city);
}