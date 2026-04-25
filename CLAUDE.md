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
├── tests/
│   ├── postman/                    # Coleção Newman para testes de integração
│   └── selenium/                   # Testes E2E Selenium (OAuth2/PKCE flow)
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

### Testes Selenium (E2E)

```bash
cd tests/selenium
pip install -r requirements.txt

# Headless (padrão / CI)
pytest test_login.py -v

# Com navegador visível (debug): comentar --headless=new no conftest.py
```

Pré-condições: `npm run dev` em `:3000` + stack Docker (`postgres keycloak auth-service api-gateway`).

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

### Frontend — OAuth2/OIDC PKCE

Next.js 15 sem biblioteca OAuth — usa Web Crypto API nativa (mais educativo, zero dependências extras).

```
:3000/            mount → useEffect detecta ausência de token
  → gera code_verifier + code_challenge (PKCE, Web Crypto)
  → salva code_verifier no sessionStorage
  → redireciona automaticamente para :8180?code_challenge=...&client_id=study-api
      preenche usuário/senha no form nativo do Keycloak
  → :3000/callback?code=...
      POST ao Keycloak: code + code_verifier → access_token + refresh_token + id_token
      salva tokens no sessionStorage
  → :3000/dashboard
      GET /api/auth/introspect (proxy Next.js → api-gateway → auth-service)
      exibe preferredUsername, roles, exp
      botão Sair → end_session Keycloak (limpa cookie SSO) → :3000/
```

Proxy em `next.config.ts`: `/api/auth/*` → `http://localhost:8080/auth/*` (evita CORS no dashboard).
A troca do code vai direto ao Keycloak — `webOrigins: ["*"]` no realm permite isso.

**Tokens salvos no sessionStorage** (não localStorage): escopo de aba, não persiste entre sessões.
`id_token` é salvo além de `access_token` e `refresh_token` — necessário para o `id_token_hint` no logout.

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

## Keycloak — Gotchas de Configuração

### KC_HOSTNAME obrigatório no docker-compose

```yaml
keycloak:
  environment:
    KC_HOSTNAME: http://localhost:8180   # opção v2 (Keycloak 25+)
```

**Por quê:** sem `KC_HOSTNAME`, o Keycloak usa o hostname da requisição como issuer (`iss`) do JWT.
Requests do browser chegam como `localhost:8180`; chamadas internas (auth-service) chegam como
`keycloak:8180`. Os tokens ficam com issuers diferentes.

Consequência prática: o `id_token_hint` enviado no `end_session` tem `iss=http://localhost:8180/...`
mas o Keycloak valida contra `iss=http://keycloak:8180/...` → mismatch → Keycloak exibe página de
confirmação em vez de redirecionar automaticamente para `post_logout_redirect_uri` → SSO não encerra
→ próximo login usa auto-login silencioso.

`KC_HOSTNAME_URL` era a opção v1 (removida no Keycloak 25+). Usar `KC_HOSTNAME` (v2).

### Logout RP-Initiated (Keycloak 26.x)

Para que o Keycloak redirecione automaticamente ao `post_logout_redirect_uri` sem exibir
confirmação, o request ao endpoint `logout` precisa incluir `client_id` E (opcionalmente)
`id_token_hint`:

```ts
const params = new URLSearchParams({
  post_logout_redirect_uri: "http://localhost:3000",
  client_id: CLIENT_ID,
  ...(idToken ? { id_token_hint: idToken } : {}),
});
window.location.href = `${END_SESSION_URL}?${params}`;
```

A chamada de revogação do `refresh_token` ao auth-service deve ser **fire-and-forget** (sem `await`).
Com `await`, o redirect aguarda o RestClient do auth-service resolver o Keycloak interno — sem timeout
configurado, isso pode bloquear por >30s.

## Testes Selenium — Padrões e Gotchas

### Arquivos

```
tests/selenium/
├── requirements.txt    # selenium==4.21.0, pytest==8.2.0
├── conftest.py         # fixture driver (headless), helpers login() / wait_for_url()
└── test_login.py       # 8 testes: landing → login → dashboard → logout → admin → wrong-pass
```

### Padrão de espera pós-logout (SSO redirect)

O redirect de `end_session` (`:8180` → `:3000`) pode levar 25–30s. Usar dois `WebDriverWait`
separados evita timeout quando o render da página consome o tempo que sobrou:

```python
# CORRETO: separa tempo do redirect do tempo de render
driver.find_element(By.CSS_SELECTOR, '[data-testid="logout-button"]').click()
wait_for_url(driver, "localhost:3000", timeout=30)   # aguarda redirect
WebDriverWait(driver, 20).until(                     # aguarda render
    EC.visibility_of_element_located((By.CSS_SELECTOR, '[data-testid="login-button"]'))
)

# ERRADO: único wait consome todo o budget no redirect, timeout antes de render
WebDriverWait(driver, 30).until(EC.visibility_of_element_located(...))
```

### Isolação de SSO entre testes

CDP `Network.clearBrowserCookies` não limpa o SSO de forma confiável no Chrome headless.
Para testes que precisam forçar o form de login independente do estado SSO, usar `prompt=login`
diretamente na URL do Keycloak:

```python
from urllib.parse import urlencode
params = urlencode({
    "client_id": "study-api", "redirect_uri": "http://localhost:3000/callback",
    "response_type": "code", "scope": "openid", "prompt": "login",
})
driver.get(f"http://localhost:8180/realms/study/protocol/openid-connect/auth?{params}")
```

### Seletor de erro do Keycloak 26.x (tema PatternFly 5)

O tema antigo usava `.alert-error` e `#input-error`. O tema padrão do Keycloak 26.x usa:

```python
# Cobre Keycloak 26.x (PF5) e versões anteriores
EC.presence_of_element_located((By.CSS_SELECTOR, "[id^='input-error'], .alert-error"))
# No Keycloak 26.x o elemento real é #input-error-username ou #input-error-password
```

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

### Desenvolvimento Frontend — PWA, Mobile First e SPA

#### SPA (Single Page Application)

O frontend é uma SPA completa: não há recarregamento de página entre rotas.

- Navegação via `router.push()` e `<Link>` do Next.js — nunca `window.location.href` dentro do app (exceto redirects externos como o Keycloak).
- Componentes interativos marcados com `"use client"`. Componentes puramente de exibição sem estado podem ser Server Components.
- Lazy loading com `next/dynamic` para componentes pesados (ex: gráficos, editores).
- Auth guard em `useEffect` no lado cliente (padrão atual). Para produção, migrar para `middleware.ts` com validação JWT server-side.
- Nunca usar Server Actions para fluxos que envolvam `sessionStorage` ou Web Crypto — essas APIs só existem no browser.

#### Mobile First

Todo componente novo parte do menor viewport (320px) e escala para cima via `min-width`.

- Usar os breakpoints do MUI v6 com a direção **mobile-first**: `sx={{ fontSize: { xs: '0.9rem', md: '1rem' } }}`.
- Tamanho mínimo de área clicável: **48×48px** (Material Design) — nenhum botão, ícone ou link deve ser menor.
- Evitar interações exclusivas de hover (``:hover`` sem equivalente `:focus-visible` ou `onClick` mobile).
- Testar no Chrome DevTools com perfis: iPhone SE (375px), Pixel 7 (412px) e tablet (768px).
- `<meta name="viewport" content="width=device-width, initial-scale=1">` obrigatório no `<head>` (já presente via Next.js `Metadata`).
- Larguras em `%`, `vw` ou props MUI (`width: '100%'`) — nunca pixels fixos em containers de layout.

#### PWA (Progressive Web App)

O app deve ser instalável e funcional offline para as rotas estáticas.

- **`public/manifest.json`** obrigatório com os campos: `name`, `short_name`, `start_url: "/"`, `display: "standalone"`, `theme_color`, `background_color` e `icons` (192×192 e 512×512 — o ícone 512 com `"purpose": "maskable"` é obrigatório para instalar no Android).
- **`<link rel="manifest">`** e **`<meta name="theme-color">`** no `<head>` global (`app/layout.tsx`).
- **Service Worker** via `next-pwa` (wrapper sobre Workbox): adicionar ao `package.json` e configurar em `next.config.ts`. Estratégia padrão: `NetworkFirst` para rotas de API, `CacheFirst` para assets estáticos.
- O SW **não deve cachear** as chamadas ao Keycloak (`:8180`) nem ao api-gateway (`:8080`) — tokens mudam a cada sessão.
- HTTPS é pré-requisito para SW em produção (já satisfeito pelo ingress TLS no K8s). Em desenvolvimento, `localhost` é exceção tratada pelos browsers.
- Checklist Lighthouse PWA deve passar antes de qualquer merge para `main`.

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
