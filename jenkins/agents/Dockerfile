# Agente Jenkins estático com todas as ferramentas necessárias para o pipeline do monorepo.
# "Estático" = a imagem é construída uma vez e reusada em todos os builds,
# diferente de agentes efêmeros que sobem e morrem por build (mais lento, mais caro).
FROM eclipse-temurin:25-jdk-noble

# Ferramentas de sistema
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    wget \
    git \
    unzip \
    python3 \
    python3-pip \
    python3-venv \
    nodejs \
    npm \
    && rm -rf /var/lib/apt/lists/*

# Maven 3.9 — mesmo major do Dockerfile de build para consistência
ARG MAVEN_VERSION=3.9.9
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    | tar -xz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

# Docker CLI — só o cliente; o daemon vem do host via socket montado no compose/k8s
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu noble stable" \
    > /etc/apt/sources.list.d/docker.list \
    && apt-get update && apt-get install -y --no-install-recommends docker-ce-cli \
    && rm -rf /var/lib/apt/lists/*

# kubectl — mesma minor do cluster-alvo
ARG KUBECTL_VERSION=v1.32.0
RUN curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
    -o /usr/local/bin/kubectl \
    && chmod +x /usr/local/bin/kubectl

# Trivy — scan de CVEs em imagens Docker pós-push
ARG TRIVY_VERSION=0.61.0
RUN curl -fsSL "https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/trivy_${TRIVY_VERSION}_Linux-64bit.tar.gz" \
    | tar -xz -C /usr/local/bin trivy

# Newman — runner CLI para coleções Postman (testes de integração)
RUN npm install -g newman@6

# Python: pytest + selenium para testes E2E
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir pytest==8.2.0 selenium==4.21.0

ENV PATH="/opt/venv/bin:$PATH"

# Usuário não-root para execução dos builds
RUN groupadd -r jenkins && useradd -r -g jenkins -m -d /home/jenkins jenkins
USER jenkins
WORKDIR /home/jenkins

# Pré-aquecer o cache Maven com dependências comuns (opcional — acelera o primeiro build)
# RUN mvn dependency:resolve ... (requer acesso ao pom.xml em tempo de build)
