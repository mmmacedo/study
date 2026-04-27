# =============================================================================
# Dockerfile — api-gateway
#
# Build multi-stage: reduz o tamanho final da imagem separando a fase de
# compilação (JDK + Maven) da fase de execução (JRE apenas).
#
# Contexto de build: backend/
#   O contexto deve ser o diretório backend/ (não api-gateway/) porque o
#   Maven precisa do parent pom.xml para resolver versões de dependências.
#   No docker-compose: context: backend, dockerfile: api-gateway/Dockerfile
#
# Para build manual:
#   docker build -f api-gateway/Dockerfile -t api-gateway:local .
#   (executar a partir de backend/)
# =============================================================================


# =============================================================================
# Stage 1: BUILD
# =============================================================================

FROM maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /build

# --- Camada de dependências (cache layer) ---
#
# Copiamos APENAS os arquivos pom.xml antes do código-fonte para aproveitar
# o cache do Docker. Se nenhum pom.xml mudou, o Maven não baixa dependências
# novamente — os 300+ MB ficam em cache entre builds.
COPY pom.xml ./
COPY api-gateway/pom.xml api-gateway/
# O parent pom.xml declara todos os módulos — o Maven valida a existência de cada um
# ao resolver o reactor. Sem este COPY, o build falha com
# "Child module /build/user-service does not exist".
COPY user-service/pom.xml user-service/
COPY auth-service/pom.xml auth-service/
COPY stream-service/pom.xml stream-service/

# Pré-baixa apenas as deps do módulo api-gateway e seus pais.
# dependency:resolve em vez de dependency:go-offline: baixa apenas as
# dependências compile/runtime/test, não os plugins de qualidade (OWASP,
# SpotBugs) que podem estar indisponíveis na rede do container de build.
RUN mvn -pl api-gateway -am dependency:resolve -q

# --- Camada de código-fonte ---
#
# Copiado depois das dependências: mudanças no código invalidam apenas
# esta layer e as posteriores, sem invalidar o cache de dependências.
COPY api-gateway/src api-gateway/src

# Compila e empacota como fat JAR.
# -DskipTests: testes já rodaram no CI antes do build da imagem.
# Rodar aqui exigiria Redis + Docker-in-Docker (Testcontainers).
RUN mvn -pl api-gateway -am package -DskipTests -q


# =============================================================================
# Stage 2: RUNTIME
# =============================================================================

FROM eclipse-temurin:25-jre-alpine
# JRE 25 alinhado com o build stage (JDK 25). Sem compilador, sem ferramentas
# de dev — menor superfície de ataque e imagem mais leve.

WORKDIR /app

# --- Usuário não-root (segurança) ---
#
# Princípio de menor privilégio: o processo da JVM não precisa de privilégios
# de root. Um exploit no código não comprometeria o host via escape de container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copia apenas o JAR final do stage de build — sem código-fonte, pom.xml
# ou dependências não-empacotadas.
COPY --from=build /build/api-gateway/target/*.jar app.jar

# --- Configuração da JVM para containers ---
#
# UseContainerSupport: JVM respeita os cgroup limits do container (não do host).
# MaxRAMPercentage=75.0: heap usa até 75% da memória do container.
# ZGC generacional (Java 25): coletor de baixa latência recomendado para
# serviços com SLAs — pausa máxima de ~1ms independente do heap size.
#
# Nota: sem -Xmx hardcoded — MaxRAMPercentage se adapta automaticamente
# ao container, independente do Kubernetes Memory limit definido no deployment.yaml.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
