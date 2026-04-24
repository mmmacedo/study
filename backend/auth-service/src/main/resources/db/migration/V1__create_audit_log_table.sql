-- =============================================================================
-- V1__create_audit_log_table.sql
--
-- Audit log de eventos de autenticação.
-- Registra cada operação de token, refresh, logout e introspect com resultado.
--
-- Decisão de design:
--   Esta tabela é append-only — nunca atualizamos registros existentes.
--   O campo updated_at existe por convenção de auditoria (padrão do projeto),
--   mas na prática sempre será igual ao created_at.
--
-- Índices:
--   event_type — filtros por tipo de evento no dashboard de auditoria
--   username   — busca de histórico por usuário
--   created_at — queries temporais (últimas N horas, rollup diário)
-- =============================================================================

CREATE TABLE auth_audit_log (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(20) NOT NULL,
    username   VARCHAR(255),
    ip_address VARCHAR(45),      -- IPv4 (15 chars) ou IPv6 (45 chars)
    success    BOOLEAN     NOT NULL,
    details    TEXT,             -- mensagem de erro quando success = false
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_event_type ON auth_audit_log (event_type);
CREATE INDEX idx_audit_log_username   ON auth_audit_log (username);
CREATE INDEX idx_audit_log_created_at ON auth_audit_log (created_at);
