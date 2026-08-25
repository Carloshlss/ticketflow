package com.ticketflow.api.domain.event;

// [JPA] Toda anotação de mapeamento vem de jakarta.persistence.
// (Se você ver "javax.persistence" em algum tutorial, é pré-Spring Boot 3.)
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * [JPA] @Entity marca esta classe como mapeada para uma tabela.
 * [JPA] @Table define o nome real da tabela. Sem isso, o Hibernate deriva do
 *       nome da classe — sempre seja explícito, evita surpresa em refactor.
 */
@Entity
@Table(name = "event")
// [SPRING DATA] Habilita os callbacks de auditoria (@CreatedDate/@LastModifiedDate).
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
// [JPA - OBRIGATÓRIO] Construtor sem argumentos. O Hibernate usa REFLECTION
// para instanciar a entidade ao ler do banco, e precisa dele.
// PROTECTED (não private) porque o Hibernate cria proxies via subclasse.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder  // [LOMBOK] Padrão Builder: legibilidade com muitos campos
public class Event {

    /**
     * [JPA] @Id = chave primária.
     * [JPA] @GeneratedValue com IDENTITY: delega a geração ao banco (BIGSERIAL).
     *
     * ⚠️ Detalhe de performance que quase ninguém sabe: IDENTITY IMPEDE o
     * batch de INSERTs do Hibernate, porque ele precisa do ID de volta a cada
     * insert. Para inserção em massa, use estratégia SEQUENCE com allocationSize.
     * Aqui IDENTITY está ótimo (inserts unitários).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * [JPA] Espelha as constraints do banco. nullable=false gera o NOT NULL
     * e — mais importante para nós — o ddl-auto:validate CONFERE isso no startup.
     * Se a entidade divergir da migration, a app não sobe. Rede de proteção grátis.
     */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    // columnDefinition = SQL bruto para tipos que o JPA não abstrai bem.
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "venue", nullable = false, length = 200)
    private String venue;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    /**
     * [JAVA 8+ TIME API] Instant = ponto na linha do tempo em UTC.
     * Mapeia para TIMESTAMPTZ. Nunca use java.util.Date (mutável e legado).
     * Regra: Instant no banco, conversão para fuso local só na exibição.
     */
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    // BigDecimal + NUMERIC(12,2). Dinheiro nunca em double.
    @Column(name = "ticket_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal ticketPrice;

    @Column(name = "total_tickets", nullable = false)
    private Integer totalTickets;

    @Column(name = "available_tickets", nullable = false)
    private Integer availableTickets;

    /**
     * [JPA] @Enumerated(EnumType.STRING) — grava o NOME do enum.
     * ⚠️ NUNCA use EnumType.ORDINAL (o default!): grava a POSIÇÃO (0,1,2...).
     * Se alguém reordenar o enum, todos os dados históricos ficam errados
     * silenciosamente. É um dos bugs mais cruéis do JPA.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    // [SPRING DATA AUDITING] Preenchido no INSERT. updatable=false protege.
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Atualizado em todo UPDATE.
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "organizer_name", nullable = false, length = 50)
    private String organizerName;

    /**
     * [JPA] @Version = LOCK OTIMISTA.
     * Como funciona: o Hibernate adiciona a versão no WHERE do UPDATE:
     *   UPDATE event SET ..., version = 6 WHERE id = 1 AND version = 5
     * Se outra transação já atualizou (version virou 6), 0 linhas são afetadas
     * e o Hibernate lança OptimisticLockException.
     *
     * "Otimista" = assume que conflito é raro, não bloqueia nada, só detecta.
     * "Pessimista" (SELECT ... FOR UPDATE) = bloqueia a linha de fato.
     * Fase 8 usa isso para garantir que o último ingresso não seja vendido 2x.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ===================================================================
    // MÉTODOS DE NEGÓCIO
    // [DDD / CLEAN CODE] A entidade NÃO é um saco de getters e setters.
    // Ela protege suas próprias invariantes. Isso é "Rich Domain Model",
    // o oposto do "Anemic Domain Model" (entidade burra + service gigante).
    // ===================================================================

    /** Regra: só compra em evento PUBLISHED com estoque suficiente. */
    public boolean canSellTickets(int quantity){
        return this.status == EventStatus.PUBLISHED
                && this.availableTickets >= quantity
                && quantity > 0;
    }

    /**
     * Reserva ingressos e ajusta o status. Toda a regra em UM lugar —
     * impossível um caller esquecer de marcar SOLD_OUT.
     */
    public void reserveTickets(int quantity){
        if(!canSellTickets(quantity)){
            throw new IllegalStateException("Cannot reserve " + quantity + " tickets for event " + this.id);
        }
        this.availableTickets -= quantity;
        if (this.availableTickets == 0){
            this.status = EventStatus.SOLD_OUT;
        }
    }

    /** Devolve ingressos (pagamento falhou / cancelamento). */
    public void releaseTickets(int quantity){
        this.availableTickets = Math.min(this.availableTickets + quantity, this.totalTickets);
        if(this.status == EventStatus.SOLD_OUT && this.availableTickets > 0){
            this.status = EventStatus.PUBLISHED;
        }
    }

    // ===================================================================
    // [JPA - PEGADINHA CLÁSSICA DE ENTREVISTA] equals/hashCode em entidade.
    //
    // Por que NÃO usar @EqualsAndHashCode do Lombok nem incluir todos os campos:
    //   1. Entidade transient tem id == null; após o save o id aparece.
    //      Se o hashCode depende de campos mutáveis, o objeto "se perde"
    //      dentro de um HashSet.
    //   2. Comparar todos os campos dispara o carregamento de relações LAZY,
    //      gerando queries inesperadas.
    //
    // Solução: comparar SOMENTE o id, e hashCode CONSTANTE por classe.
    // (getClass() != o.getClass() falharia com proxies do Hibernate — por isso
    //  usamos instanceof, que aceita a subclasse-proxy.)
    // ===================================================================
    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(!(o instanceof Event other)) return false;   // [JAVA 16+] pattern matching
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode(){
        // Constante: contrato do hashCode preservado antes e depois de ganhar id.
        return Objects.hash(getClass());
    }
}