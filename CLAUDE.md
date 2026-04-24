# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Visão Geral

Monorepo de estudo focado em CI/CD, segurança e escalabilidade. Stack: Next.js 15 (frontend) + 4 microserviços Java 25/Spring Boot 3.4 (backend) + Jenkins + Docker + Kubernetes. Todos os conceitos-chave são documentados com comentários explicativos diretamente nos arquivos.

## Estrutura do Repositório

```
├── frontend/                       # Next.js 15 + TypeScript + Material UI v6
├── backend/
│   ├── pom.xml                     # Parent POM (versões centralizadas)
│   ├── api-gateway/                # Spring Cloud Gateway — único ponto de entrada
│   ├── auth-service/               # OAuth2/OIDC + JWT via Keycloak
│   ├── user-service/               # REST CRUD (Spring MVC + JPA + Flyway)
│   └── stream-service/             # SSE + mensageria reativa (Spring WebFlux + Spring Cloud Stream)
├── k8s/
│   ├── namespace.yaml              # Isolamento staging/production + ResourceQuota
│   ├── configmap.yaml              # Config não-sensível compartilhada
│   └── <service>/                  # deployment.yaml, service.yaml, hpa.yaml por serviço
├── jenkins/
│   ├── agents/Dockerfile           # Imagem do agente Jenkins estático
│   └── pipelines/                  # Pipelines isolados por serviço
├── infra/
│   ├── postgres/init.sql           # Cria bancos por serviço (database-per-service)
│   ├── keycloak/realm-export.json  # Realm pré-configurado com roles e users de teste
│   └── otel/collector.yaml         # OpenTelemetry Collector — pipeline de telemetria
├── tests/postman/                  # Coleção Newman para testes de integração
├── Jenkinsfile                     # Pipeline principal do monorepo
└── docker-compose.yml              # Stack local completa com perfis (app, quality)
```

## Comandos Essenciais

### Stack Local

```bash
# Infraestrutura base (banco, cache, broker, identidade)
# Kafka roda em modo KRaft — sem Zookeeper (Kafka 3.x+)
docker compose up -d postgres redis kafka keycloak

# Observabilidade (OTel Collector + Jaeger)
docker compose up -d otel-collector jaeger

# Aplicação completa (requer infra rodando)
docker compose --profile app up -d

# Ferramentas de qualidade (SonarQube)
docker compose --profile quality up -d

# Rebuild de um serviço específico
docker compose up -d --build user-service

# Logs em tempo real
docker compose logs -f api-gateway user-service
```

### Frontend

```bash
cd frontend
npm ci
npm run dev             # :3000
npm run build           # static export para /out
npm run lint
npm test                # Jest
npm test -- --testPathPattern=UserList   # teste único
npm test -- --coverage --watchAll=false  # cobertura (modo CI)
```

### Backend (Maven)

```bash
cd backend

# Build de todos os módulos
mvn clean package -DskipTests -T 1C

# Testa um módulo específico
mvn -pl user-service -am test

# Roda uma classe de teste específica
mvn -pl user-service test -Dtest=UserControllerIT

# Roda localmente com Spring Boot
mvn -pl user-service spring-boot:run

# Análise SonarQube local
mvn sonar:sonar -Dsonar.projectKey=study -Dsonar.host.url=http://localhost:9000

# Scan OWASP de dependências
mvn -pl user-service dependency-check:check
```

### Kubernetes

```bash
# Aplica todos os manifests (staging)
kubectl apply -f k8s/ -n staging --recursive

# Deploy com substituição de variáveis (igual ao Jenkinsfile)
export IMAGE_TAG=42-abc1234 REGISTRY=registry.example.com
for f in k8s/*/deployment.yaml; do envsubst < $f | kubectl apply -n staging -f -; done

# Status de rollout
kubectl rollout status deployment/user-service -n staging

# Rollback de emergência
kubectl rollout undo deployment/user-service -n staging

# Port-forward para desenvolvimento
kubectl port-forward svc/user-service 8082:8082 -n staging

# Logs de todos os pods do serviço
kubectl logs -f -l app=user-service -n staging
```

## Arquitetura

### Fluxo de Request

```
Browser/Cliente
    → Ingress (TLS termination, nginx)
        → api-gateway:8080
            → Valida JWT via Keycloak JWKS endpoint
            → Rate limiting (Redis token bucket)
            → Circuit breaker (Resilience4j)
            → Roteia para:
                auth-service:8081    (/auth/**)
                user-service:8082    (/api/users/**)
                stream-service:8083  (/stream/**)
```

### Autenticação (OAuth2/OIDC)

- **Keycloak** é o Identity Provider (IdP). Emite JWTs no padrão OIDC.
- **auth-service** é um wrapper REST: expõe `/auth/token`, `/auth/refresh`, `/auth/logout`, `/auth/introspect`. Delega ao Keycloak via Token Endpoint.
- **api-gateway** valida a assinatura JWT usando o JWKS endpoint do Keycloak (`/realms/study/protocol/openid-connect/certs`). Rejeita requests sem token válido antes de rotear.
- **user-service** e demais serviços confiam no gateway: validam apenas as claims do JWT (`@PreAuthorize("hasRole('ADMIN')")`).
- Keycloak local: `http://localhost:8180` — admin/admin. Realm `study` está em `infra/keycloak/realm-export.json`.

### Persistência (Database-per-Service)

Cada serviço tem seu próprio banco no PostgreSQL — nunca compartilham schema:

| Serviço | Banco | Migrations |
|---|---|---|
| auth-service | `auth_db` | `V1__init_auth_schema.sql` |
| user-service | `user_db` | `V1__create_users_table.sql` |
| stream-service | — | somente broker |

> **auth_db**: armazena **audit log de autenticação** (eventos de login, logout e refresh de token
> com IP, timestamp e resultado). Usuários e roles vivem no Keycloak — o auth-service é stateless
> para dados de identidade, mas stateful para auditoria.

Flyway gerencia as migrations. Spring JPA usa `ddl-auto: validate` (nunca modifica schema automaticamente).

### Mensageria — Abstração de Broker

**Princípio:** o código da aplicação nunca nomeia nem importa classes de um broker específico.
A mensageria é tratada como um componente de infraestrutura intercambiável.

**Como funciona:** o `stream-service` usa **Spring Cloud Stream**, que fornece uma camada de abstração
sobre o broker. A aplicação escreve contra interfaces funcionais Java puras (`Supplier`, `Consumer`,
`Function`) — sem anotações, sem APIs proprietárias de Kafka, RabbitMQ ou Redis.

```
Código Java:
  Consumer<MensagemDeEvento>   →  Spring Cloud Stream (binding)
                                      →  Binder (plugável via pom.xml + config)
                                            ├── spring-cloud-stream-binder-kafka   (Apache Kafka)
                                            ├── spring-cloud-stream-binder-rabbit  (RabbitMQ)
                                            └── binder Redis Streams               (Redis 5+)
```

**Trocar de broker = mudar 2 coisas:**
1. Dependência no `pom.xml` (binder)
2. Configuração no `application.yml` (endereço e opções do broker)

O código Java dos producers e consumers permanece **idêntico**.

**Regras para este projeto:**
- Nunca importar `org.apache.kafka.*`, `com.rabbitmq.*` ou qualquer classe de broker diretamente
- Nunca mencionar o nome do broker no código de negócio (use `MessageBroker`, `EventBus`, `Broker`)
- Configurações de broker ficam exclusivamente em `application.yml` e variáveis de ambiente
- Testes usam Testcontainers com o binder ativo no perfil de teste — sem mocks de broker

> **Redis e mensageria:** o Redis no `docker-compose.yml` é exclusivamente para cache de sessão
> e rate-limiting no api-gateway. Se o binder Redis Streams for escolhido como broker,
> ele requer uma instância separada (ou database isolado via `redis-url` com `/1`, `/2` etc.)
> para não misturar responsabilidades.

### Reatividade

`stream-service` usa Spring WebFlux + Project Reactor. O endpoint SSE (`GET /stream/events`)
faz bridge do broker para HTTP. Testcontainers sobe o broker real nos testes de integração.

### Observabilidade (OpenTelemetry)

O foco é o protocolo e o pipeline OTEL — não o backend de visualização.

```
Serviços Spring Boot
    → OTLP HTTP (:4318)
        → OpenTelemetry Collector
              ├── traces  → Jaeger (:4317 gRPC interno)
              ├── métricas → debug exporter (stdout do Collector)
              └── logs    → debug exporter (stdout do Collector)

Jaeger UI acessível em :16686
```

**Por que Jaeger e não Grafana/Prometheus/Tempo?**
Jaeger é um backend de tracing que aceita OTLP nativamente (desde v1.35),
tem storage e UI próprios em um único container, e mantém o foco no OTEL
sem introduzir ferramentas extras (Prometheus, Loki, Grafana, Promtail, Tempo).
Para este projeto de estudo, o objetivo é entender o pipeline OTEL — o backend
de visualização é um detalhe de infraestrutura.

**Três sinais OTEL:**
- **Traces** → OTLP ao Collector → Jaeger (visualização de spans e latência)
- **Métricas** → OTLP ao Collector → debug exporter (estudo do formato OTLP)
- **Logs** → stdout em formato ECS (Spring Boot 3.4 structured logging). Não há push OTLP
  de logs — Spring Boot não inclui exportador OTLP de logs nativamente. O `trace.id` é
  embutido automaticamente no JSON, permitindo correlação manual pelo timestamp.

**Dependências Spring Boot para OTEL:**
- `micrometer-tracing-bridge-otel` — ponte entre Micrometer Tracing API e OTel SDK
- `opentelemetry-exporter-otlp` — serializa e envia traces/métricas via OTLP HTTP
- `micrometer-registry-otlp` — exporta métricas via OTLP (sem Prometheus no stack)

## Pipeline CI/CD (Jenkinsfile)

### Stages e Conceitos de Estudo

| Stage | Conceito Principal |
|---|---|
| Checkout + stash | Compartilha workspace entre stages paralelas |
| Build (paralelo) | `mvn -T 1C` + `npm ci` simultâneos |
| Test (paralelo) | JUnit XML publicado; Testcontainers para banco real |
| Code Quality | SonarQube `waitForQualityGate` barra promoção |
| Security Scan | OWASP CVEs em deps + `npm audit` |
| Docker Build & Push | Multi-stage build; tag `buildNum-gitSha` |
| Trivy Image Scan | CVEs em imagens Docker pós-push |
| Deploy Staging | `envsubst` injeta `IMAGE_TAG` nos manifests YAML |
| Integration Tests | Newman/Postman contra staging real |
| Deploy Production | `when{branch 'main'}` + `input` (aprovação manual) |
| post.always | Arquiva JARs + Slack; `cleanWs()` libera espaço |

Pipelines isolados por serviço em `jenkins/pipelines/` permitem redeploy sem rodar o pipeline completo.

## Docker Multi-Stage Build

Todos os Dockerfiles seguem o padrão de 2 stages:
1. `maven:3.9-eclipse-temurin-25-alpine` → compila, gera JAR
2. `eclipse-temurin:25-jre-alpine` → apenas JRE + JAR (sem JDK, Maven, fontes)

Resultado: imagem ~200MB em vez de ~600MB. Usuário não-root (`appuser`) em runtime.

```
JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ZGenerational"
```

`UseContainerSupport` faz a JVM respeitar os limites do container. ZGC generacional é o coletor
de baixa latência recomendado no Java 25 para serviços com SLAs de latência.

## Ambiente Windows — Testcontainers

### Problema: Docker Desktop 29.x + docker-java 3.4.x

Docker Desktop 29.x elevou `MinAPIVersion` para 1.44. O docker-java (usado internamente pelo
Testcontainers) envia requests com versão 1.41 por padrão (`/v1.41/_ping`). Docker Desktop responde
HTTP 400 com body vazio. O `DOCKER_API_VERSION` env var **não funciona** porque o Testcontainers
usa uma versão *shaded* (embutida) do docker-java que ignora configurações externas.

### Solução: proxy Python no WSL2 (porta 2376)

O proxy reescreve `/v1.XX/` → `/v1.44/` em **todos** os chunks TCP (não só o primeiro —
HTTP keep-alive reusa a conexão, e patches só no primeiro chunk fazem requisições subsequentes
falharem silenciosamente).

**1. Script `/tmp/docker_proxy.py` no WSL2:**

```python
import socket, threading, re
UNIX = '/var/run/docker.sock'
PORT = 2376
def pipe(src, dst, do_patch):
    try:
        while True:
            d = src.recv(65536)
            if not d: break
            if do_patch:
                d = re.sub(rb'/v1\.\d+/', b'/v1.44/', d)
            dst.sendall(d)
    except: pass
    finally:
        for s in (src, dst):
            try: s.close()
            except: pass
def handle(client):
    srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    srv.connect(UNIX)
    threading.Thread(target=pipe, args=(client, srv, True), daemon=True).start()
    threading.Thread(target=pipe, args=(srv, client, False), daemon=True).start()
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind(('0.0.0.0', PORT))
sock.listen(50)
while True:
    c, _ = sock.accept()
    threading.Thread(target=handle, args=(c,), daemon=True).start()
```

**2. Iniciar o proxy (antes de `mvn test`):**

```bash
wsl bash -c "nohup python3 /tmp/docker_proxy.py > /tmp/docker_proxy.log 2>&1 &"
# verificar que está ouvindo:
wsl bash -c "ss -tlnp | grep 2376"
```

**3. `C:\Users\<user>\.testcontainers.properties`:**

```
tc.host=tcp://localhost:2376
```

> Usar `tc.host` (não `docker.host` — propriedade errada é silenciosamente ignorada).
> O proxy precisa ser reiniciado após reboot do WSL2 — o script fica em `/tmp/docker_proxy.py`.

## Kubernetes — Conceitos em `k8s/`

- **namespace.yaml**: ResourceQuota limita CPU/memória por namespace.
- **deployment.yaml**: `maxUnavailable: 0` garante zero-downtime. `affinity.podAntiAffinity` distribui pods em nodes diferentes.
- **Probes**: `startupProbe` evita que `livenessProbe` mate pods lentos na inicialização. `readinessProbe` remove do Service até Flyway terminar.
- **hpa.yaml**: Autoscaling por CPU com `stabilizationWindowSeconds` para evitar flapping.
- **ingress.yaml**: `cert-manager` provisionamento automático de TLS. Rate limiting no layer de ingress.
- **Variáveis nos manifests**: `$IMAGE_TAG` e `$REGISTRY` são substituídos pelo `envsubst` no Jenkinsfile — os YAMLs em git não contêm tags de imagem hardcodadas.

## Portas dos Serviços

| Serviço | Porta |
|---|---|
| api-gateway | 8080 |
| auth-service | 8081 |
| user-service | 8082 |
| stream-service | 8083 |
| frontend (dev) | 3000 |
| Keycloak | 8180 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Broker | 9092 ¹ |
| OTel Collector HTTP | 4318 |
| OTel Collector gRPC | 4317 |
| Jaeger UI | 16686 |
| SonarQube | 9000 |

> ¹ Porta do broker varia conforme o binder: Kafka=9092, RabbitMQ=5672, Redis=6379.
> O docker-compose usa Kafka KRaft (porta 9092) como broker padrão.

## Guia de Projeto

- Arquitetura em camadas no backend: `controller → service → repository`. Lógica de negócio só em `service`.
- Injeção via construtor (Lombok `@RequiredArgsConstructor`).
- Testes de integração com Testcontainers (banco real, não mock). `@SpringBootTest` + `@ServiceConnection`.
- Frontend: sem `any` no TypeScript. Componentes pequenos e reutilizáveis. Erros tratados explicitamente.
- Exceções padronizadas com RFC 7807 Problem Details (`ProblemDetail` do Spring 6).
- Mensageria sempre via abstração Spring Cloud Stream — nunca importar APIs de broker diretamente.
- Métricas exportadas via OTLP (`micrometer-registry-otlp`) — sem Prometheus no stack.

### Entidades JPA — campos obrigatórios

Toda entidade `@Entity` deve ter os campos de auditoria abaixo. Sem exceção.

```java
@CreationTimestamp
@Column(nullable = false, updatable = false)
private OffsetDateTime createdAt;

@UpdateTimestamp
@Column(nullable = false)
private OffsetDateTime updatedAt;
```

E na migration SQL correspondente:

```sql
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

**Por quê:**
- `@CreationTimestamp` / `@UpdateTimestamp` são anotações Hibernate que preenchem o campo
  automaticamente antes do INSERT/UPDATE — sem código manual no service.
- `OffsetDateTime` (com fuso) em vez de `LocalDateTime` (sem fuso): o banco armazena em UTC
  via `TIMESTAMPTZ`; a JVM lê no fuso da aplicação. Sem isso, timestamps ambíguos em ambientes
  multi-região.
- `updatable = false` em `createdAt` garante que um UPDATE acidental não sobrescreva a data de criação.
- Os campos de auditoria pertencem à **entidade**, não ao DTO de resposta — exponha apenas o que
  fizer sentido para o cliente (`createdAt` geralmente sim, `updatedAt` depende do contexto).

### SecurityConfig — REST API JWT stateless

Todo `SecurityFilterChain` para serviço REST com JWT deve ter CSRF desabilitado e sessão STATELESS.
Sem isso, POST/PUT/DELETE em testes com `@WithMockUser` retornam 403 — erro difícil de diagnosticar
pois CSRF é habilitado por padrão no Spring Security.

```java
http
    .csrf(csrf -> csrf.disable())
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health/**").permitAll()
        .anyRequest().authenticated()
    )
    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
```

### Flyway 10.x — PostgreSQL 15+

Todo serviço que usa PostgreSQL 15+ precisa do módulo separado no `pom.xml`:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <!-- versão gerenciada pelo BOM do Spring Boot — não declarar manualmente -->
</dependency>
```

Sem isso, o startup lança `BeanCreationException: Unsupported Database: PostgreSQL 16.x`
porque no Flyway 10.x (Spring Boot 3.3+) o suporte a bancos específicos foi separado em módulos.

### GlobalExceptionHandler — Collectors.toMap() com campos duplicados

```java
.collect(Collectors.toMap(
    fe -> fe.getField(),
    fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "inválido",
    (existing, duplicate) -> existing  // mantém o primeiro erro por campo
))
```

Sempre usar merge function. Um campo com duas anotações que falham ao mesmo tempo (ex: `@NotBlank` +
`@Size` com valor vazio) gera duas entradas com a mesma chave e `Collectors.toMap()` sem merge
lança `IllegalStateException`.

### Dockerfile — dependency:resolve vs dependency:go-offline

Usar `dependency:resolve` no Dockerfile, **não** `dependency:go-offline`.

```dockerfile
RUN mvn -pl user-service -am dependency:resolve -q
```

`dependency:go-offline` tenta baixar todos os plugins do POM (SpotBugs, OWASP Dependency-Check)
que podem estar indisponíveis na rede do container de build. `dependency:resolve` baixa apenas
as dependências compile/runtime/test — tudo que o `package` precisa.
