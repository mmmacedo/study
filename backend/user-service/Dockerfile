# =============================================================================
# Dockerfile — user-service
#
# Build multi-stage: reduz o tamanho final da imagem separando a fase de
# compilação (JDK + Maven) da fase de execução (JRE apenas).
#
# Contexto de build: backend/
#   O contexto deve ser o diretório backend/ (não user-service/) porque o
#   Maven precisa do parent pom.xml para resolver versões de dependências.
#   No docker-compose: context: backend, dockerfile: user-service/Dockerfile
#
# Para build manual:
#   docker build -f user-service/Dockerfile -t user-service:local .
#   (executar a partir de backend/)
# =============================================================================


# =============================================================================
# Stage 1: BUILD
# =============================================================================

FROM maven:3.9-eclipse-temurin-25-alpine AS build
# eclipse-temurin:25-alpine disponível desde out/2025 — alinhado com <java.version>25</java.version>
# no pom.xml. Mantemos Maven 3.9 (LTS) + Java 25 (LTS de set/2025).

WORKDIR /build

# --- Camada de dependências (cache layer) ---
#
# Copiamos APENAS os arquivos pom.xml antes do código-fonte.
# O Docker cacheia cada instrução como uma "layer" imutável.
# Se nenhum pom.xml mudou, `mvn dependency:go-offline` não roda novamente —
# as dependências ficam na cache do Docker. Isso é a técnica mais impactante
# para reduzir tempo de build em CI/CD.
#
# Sem essa separação: qualquer mudança no código-fonte invalidaria a cache
# de dependências (300+ MB baixados a cada push).
COPY pom.xml ./
COPY user-service/pom.xml user-service/
# O parent pom.xml declara todos os módulos — o Maven valida a existência de cada um
# ao resolver o reactor. Sem este COPY, o build falha com
# "Child module /build/api-gateway does not exist".
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY stream-service/pom.xml stream-service/

# Pré-baixa as dependências do projeto (compile/runtime/test) para o repositório local.
# dependency:resolve em vez de dependency:go-offline:
#   dependency:go-offline tenta baixar TODOS os plugins declarados no POM — incluindo
#   ferramentas de qualidade (spotbugs, OWASP) que não são necessárias na imagem e que
#   podem estar indisponíveis na rede do container de build.
#   dependency:resolve baixa apenas as deps do projeto, que é tudo que o package precisa.
RUN mvn -pl user-service -am dependency:resolve -q

# --- Camada de código-fonte ---
#
# Copiado depois das dependências para maximizar reuso de cache.
# Mudanças no código-fonte invalidam apenas esta layer e as posteriores.
COPY user-service/src user-service/src

# Compila e empacota como JAR executável (fat JAR / uber JAR).
# -DskipTests: testes NÃO rodam no build do Docker — eles já rodaram no CI
#              antes do build da imagem. Rodar aqui duplicaria o trabalho e
#              exigiria Docker-in-Docker para o Testcontainers.
RUN mvn -pl user-service -am package -DskipTests -q


# =============================================================================
# Stage 2: RUNTIME
# =============================================================================

FROM eclipse-temurin:25-jre-alpine
# JRE 25 alinhado com o build stage (JDK 25). JRE apenas: sem compilador,
# sem ferramentas de dev — menor superfície de ataque e imagem mais leve.

WORKDIR /app

# --- Usuário não-root (segurança) ---
#
# Por padrão, containers rodam como root — qualquer exploit pode comprometer
# o host via escape de container. Criar um usuário dedicado sem privilégios
# segue o princípio de menor privilégio (Principle of Least Privilege).
#
# addgroup + adduser (Alpine): cria grupo e usuário no sistema de arquivos do Alpine.
# -S (system): usuário sem home directory e sem shell de login — adequado para serviços.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copia apenas o JAR do stage anterior — não inclui código-fonte, pom.xml,
# dependências não-empacotadas, nem qualquer artefato do JDK.
COPY --from=build /build/user-service/target/*.jar app.jar

# --- Configuração da JVM para containers ---
#
# UseContainerSupport (padrão desde Java 11):
#   A JVM lê os cgroup limits do container para dimensionar heap, thread count,
#   GC threads. Sem isso, ela leria os recursos do host e usaria memória em excesso,
#   causando OOMKilled pelo Kubernetes.
#
# MaxRAMPercentage=75.0:
#   A JVM pode usar até 75% da memória do container para o heap.
#   Os 25% restantes ficam para: stack de threads, metaspace, code cache, NIO buffers.
#   Regra prática: 60-75% para serviços com muitas threads, 70-80% para serviços simples.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Documenta a porta que o serviço escuta. Não expõe automaticamente —
# é apenas metadado para quem lê o Dockerfile e para ferramentas de orquestração.
EXPOSE 8082

# ENTRYPOINT vs CMD:
#   ENTRYPOINT: comando fixo que sempre roda (não substituível sem --entrypoint)
#   CMD: argumentos padrão, facilmente sobrescrito no docker run ou no compose
#
# Usamos `sh -c` para que $JAVA_OPTS seja expandido como variável de ambiente.
# Alternativa mais segura (sem shell): ENTRYPOINT ["java", "-jar", "app.jar"]
# mas perdemos a capacidade de passar flags via JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
