-- =============================================================================
-- V1__create_users_table.sql
--
-- Convenção de nomenclatura do Flyway:
--   V{versão}__{descrição}.sql
--   Versão: número inteiro ou decimal (V1, V2, V1.1)
--   Descrição: palavras separadas por underscores
--
-- Flyway registra cada migration executada na tabela flyway_schema_history.
-- Uma migration executada NUNCA pode ser alterada (o checksum é verificado).
-- Para alterar o schema: crie uma nova migration (V2__...).
-- =============================================================================

CREATE TABLE users
(
    -- UUID como PK: evita colisões em ambiente distribuído e não expõe
    -- contadores sequenciais (que revelam volume de dados ao cliente).
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    name       VARCHAR(150) NOT NULL,
    -- email único: enforçado no banco (não só na aplicação).
    -- Constraint no banco garante integridade mesmo com múltiplas instâncias do serviço.
    email      VARCHAR(255) NOT NULL,
    -- role: enum armazenado como texto. Valores válidos: USER, ADMIN.
    -- CHECK constraint garante que só valores conhecidos entrem no banco.
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER'
        CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN')),
    -- active: soft delete — nunca apagamos registros de usuário,
    -- apenas os desativamos. Mantém histórico e integridade referencial.
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Índice único no email: garante unicidade E acelera buscas por email.
-- Separar da PK permite adicionar partial index futuramente
-- (ex: único apenas para usuários ativos).
CREATE UNIQUE INDEX idx_users_email ON users (email);
-- Índice no role: acelera queries de "listar todos os admins"
CREATE INDEX idx_users_role ON users (role);
