-- =============================================================================
-- infra/postgres/init.sql
--
-- Script de inicialização do PostgreSQL.
-- Executado automaticamente pelo container na PRIMEIRA vez que o volume é criado.
-- O mecanismo: tudo em /docker-entrypoint-initdb.d/ roda como superuser (postgres)
-- antes do banco aceitar conexões externas.
--
-- Padrão database-per-service:
--   Cada microserviço tem seu próprio banco isolado.
--   Vantagens:
--     - Falha em um banco não afeta outros serviços
--     - Schema de cada serviço evolui independentemente (migrations separadas)
--     - Possibilidade de mover para bancos separados no futuro sem reescrever queries
--     - Cumprimento mais fácil de compliance (dados sensíveis isolados)
--
-- PostgreSQL cria automaticamente o banco "postgres" — não precisamos criá-lo.
-- Cada serviço conecta ao seu banco exclusivo via SPRING_DATASOURCE_URL.
-- =============================================================================

-- Banco do user-service
-- Armazena: usuários, roles, status ativo/inativo
-- Migrations em: backend/user-service/src/main/resources/db/migration/
CREATE DATABASE user_db;

-- Banco do auth-service (criado agora para evitar erro de conexão quando o serviço subir)
-- Armazena: audit log de eventos de autenticação (login, logout, token refresh)
-- NÃO armazena usuários — esses vivem no Keycloak.
-- Migrations em: backend/auth-service/src/main/resources/db/migration/
CREATE DATABASE auth_db;
