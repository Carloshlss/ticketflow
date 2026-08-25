-- [FLYWAY MIGRATION V1] Primeira versão do schema.
-- SQL puro e explícito: você controla tipos, índices e constraints.

CREATE TABLE event (
    -- BIGSERIAL = BIGINT + sequence automática (o auto-increment do Postgres).
    id BIGSERIAL PRIMARY KEY,

    -- VARCHAR com limite + NOT NULL: a integridade nasce NO BANCO.
    -- Validação na aplicação (Bean Validation, Fase 3) é UX, não garantia.
    -- Múltiplas instâncias da app ou um script direto no banco furam a app,
    -- mas não furam a constraint. Valide nas duas camadas.
    name VARCHAR(150) NOT NULL,
    description TEXT,
    venue VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,

    -- TIMESTAMPTZ (com timezone) e nunca TIMESTAMP simples.
    -- Guarda em UTC e converte na leitura. Se você já apanhou de horário
    -- de verão / usuário em outro fuso, foi por falta disso.
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,

    -- NUNCA use FLOAT/DOUBLE para dinheiro (erro de arredondamento binário).
    -- NUMERIC é decimal exato. Mapeia para BigDecimal no Java.
    ticket_price NUMERIC(12,2) NOT NULL,

    total_tickets INTEGER NOT NULL,
    available_tickets INTEGER NOT NULL,

    -- Status como texto + CHECK: legível em queries e validado pelo banco.
    -- (Alternativa: ENUM nativo do Postgres — mais rígido de evoluir.)
    status VARCHAR(20) NOT NULL,

    -- [AUDITORIA] Preenchidos pelo Spring Data (@CreatedDate/@LastModifiedDate).
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    -- [CONTROLE DE CONCORRÊNCIA] Usado pelo @Version do JPA (lock otimista).
    -- É a peça-chave para "dois usuários comprando o último ingresso" na Fase 8.
    version BIGINT NOT NULL DEFAULT 0,

    -- CONSTRAINTS declarativas: o banco recusa dado inconsistente, sempre.
    CONSTRAINT chk_event_status
                   CHECK (status IN ('DRAFT', 'PUBLISHED', 'SOULD_OUT', 'CANCELLED', 'FINISHED')),
    CONSTRAINT chk_event_dates
                   CHECK (ends_at > starts_at),
    CONSTRAINT chk_event_price
                   CHECK (ticket_price >= 0),
    CONSTRAINT chk_event_tickets
                   CHECK (total_tickets > 0 AND available_tickets BETWEEN 0 AND total_tickets)
);

-- [ÍNDICES] Aceleram leitura, custam na escrita e em disco.
-- Crie para colunas usadas em WHERE / ORDER BY / JOIN.
CREATE INDEX idx_event_starts_at ON event (starts_at);
CREATE INDEX idx_event_city ON event (city);
CREATE INDEX idx_event_status ON event (status);

-- Índice COMPOSTO para a query principal da vitrine:
-- "eventos PUBLISHED de uma cidade, ordenados por data".
-- A ORDEM das colunas importa: só serve para filtros que começam em city.
CREATE INDEX idx_event_city_status_starts_at ON event (city, status, starts_at);