# study-monorepo

Monorepo de estudo focado em **CI/CD, segurança e escalabilidade**.
Cada conceito é documentado com comentários explicativos diretamente nos arquivos — o código é o material de estudo.

## Stack

| Camada | Tecnologia |
|---|---|
| Frontend | Next.js 15, TypeScript, Material UI v6, React Query |
| Backend | Java 25, Spring Boot 3.4, Maven multi-module |
| Auth | Keycloak (OAuth2/OIDC), JWT |
| Banco | PostgreSQL 16, Flyway (migrations) |
| Mensageria | Kafka KRaft (via Spring Cloud Stream) |
| Observabilidade | OpenTelemetry Collector, Jaeger |
| CI/CD | Jenkins (pipelines declarativos + agentes Kubernetes) |
| Containers | Docker multi-stage, Docker Compose |
| Orquestração | Kubernetes (HPA, zero-downtime deploy, cert-manager) |
| Qualidade | SonarQube, OWASP Dependency-Check, Trivy |

## Microserviços

| Serviço | Porta | Status |
|---|---|---|
| `api-gateway` | 8080 | planejado |
| `auth-service` | 8081 | planejado |
| `user-service` | 8082 | ✅ implementado |
| `stream-service` | 8083 | planejado |

## Subir localmente

**Pré-requisitos:** Docker Desktop, JDK 25, Maven 3.9, Node 20.

```bash
# Banco de dados
docker compose up -d postgres

# user-service (requer postgres healthy)
docker compose up -d user-service

# Verificar
curl http://localhost:8082/actuator/health
```

## Testes

```bash
cd backend

# Todos os testes do user-service (Testcontainers — requer Docker)
mvn -pl user-service -am test

# Classe específica
mvn -pl user-service test -Dtest=UserControllerIT
```

> **Windows + Docker Desktop 29.x:** o Testcontainers requer um proxy WSL2 ativo.
> Veja a seção "Ambiente Windows — Testcontainers" no [CLAUDE.md](CLAUDE.md).

## Estrutura

```
├── backend/
│   ├── pom.xml              # Parent POM — versões centralizadas
│   ├── api-gateway/
│   ├── auth-service/
│   ├── user-service/        # REST CRUD (Spring MVC + JPA + Flyway)
│   └── stream-service/
├── frontend/                # Next.js 15
├── infra/
│   └── postgres/init.sql    # Cria bancos por serviço
├── k8s/                     # Manifests Kubernetes
├── jenkins/                 # Pipelines e imagem do agente
├── tests/postman/           # Coleção Newman
├── docker-compose.yml
├── CLAUDE.md                # Guia de arquitetura e padrões
└── Jenkinsfile
```

## Variáveis de ambiente relevantes

| Variável | Padrão | Descrição |
|---|---|---|
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | Fração de requests trackeados (produção: 0.05–0.1) |
| `JPA_SHOW_SQL` | `false` | Loga queries SQL no console |
| `OTEL_ENDPOINT` | `http://localhost:4318` | Endpoint do OTel Collector |
