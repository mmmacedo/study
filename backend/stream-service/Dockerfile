# =============================================================================
# Dockerfile — stream-service
#
# Build multi-stage: fase de compilação (JDK + Maven) separada da execução (JRE).
#
# Contexto de build: backend/
#   O Maven precisa do parent pom.xml para resolver versões de dependências.
#   No docker-compose: context: backend, dockerfile: stream-service/Dockerfile
#
# Para build manual:
#   docker build -f stream-service/Dockerfile -t stream-service:local .
#   (executar a partir de backend/)
# =============================================================================


# =============================================================================
# Stage 1: BUILD
# =============================================================================

FROM maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /build

# --- Camada de dependências (cache layer) ---
#
# Copiamos TODOS os pom.xml declarados no parent antes do código-fonte.
# O Maven valida a existência de cada módulo ao resolver o reactor.
# Sem os pom.xml dos módulos irmãos: "Child module /build/X does not exist".
COPY pom.xml ./
COPY stream-service/pom.xml stream-service/
COPY user-service/pom.xml user-service/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/

# Pré-baixa apenas as dependências do stream-service (não os plugins opcionais).
# dependency:resolve em vez de dependency:go-offline — veja user-service/Dockerfile.
RUN mvn -pl stream-service -am dependency:resolve -q

# --- Camada de código-fonte ---
COPY stream-service/src stream-service/src

RUN mvn -pl stream-service -am package -DskipTests -q


# =============================================================================
# Stage 2: RUNTIME
# =============================================================================

FROM eclipse-temurin:25-jre-alpine
# JRE 25 alinhado com o build stage (JDK 25). Sem compilador, sem ferramentas
# de dev — menor superfície de ataque e imagem mais leve.

WORKDIR /app

# Usuário não-root: segue o Principle of Least Privilege.
# Qualquer exploit não obtém acesso root ao host via escape de container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /build/stream-service/target/*.jar app.jar

# UseContainerSupport: JVM respeita os cgroup limits do container (não do host).
# MaxRAMPercentage=75.0: 75% da memória do container para o heap JVM.
# UseZGC + ZGenerational: coletor de baixa latência (Java 25 recomendado).
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8083

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
