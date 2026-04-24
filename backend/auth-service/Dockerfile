# =============================================================================
# Dockerfile — auth-service
#
# Build multi-stage: fase de compilação (JDK + Maven) separada da execução (JRE).
#
# Contexto de build: backend/
#   O Maven precisa do parent pom.xml para resolver versões de dependências.
#   No docker-compose: context: backend, dockerfile: auth-service/Dockerfile
#
# Para build manual:
#   docker build -f auth-service/Dockerfile -t auth-service:local .
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
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY api-gateway/pom.xml api-gateway/

# Pré-baixa apenas as dependências do auth-service (não os plugins opcionais).
# dependency:resolve em vez de dependency:go-offline — veja user-service/Dockerfile.
RUN mvn -pl auth-service -am dependency:resolve -q

# --- Camada de código-fonte ---
COPY auth-service/src auth-service/src

RUN mvn -pl auth-service -am package -DskipTests -q


# =============================================================================
# Stage 2: RUNTIME
# =============================================================================

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Usuário não-root: segue o Principle of Least Privilege.
# Qualquer exploit não obtém acesso root ao host via escape de container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /build/auth-service/target/*.jar app.jar

# UseContainerSupport: JVM respeita os cgroup limits do container (não o host).
# MaxRAMPercentage=75.0: 75% da memória do container para o heap JVM.
# UseZGC + ZGenerational: coletor de baixa latência (Java 25 recomendado).
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
