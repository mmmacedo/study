/*
 * Pipeline isolado do stream-service.
 * Permite redeploy deste serviço sem executar o pipeline completo do monorepo.
 * Útil para hotfixes, ajustes de config e atualizações independentes.
 *
 * Pré-requisitos no Jenkins:
 *   - Credential 'docker-registry' (username + password) apontando para o registry
 *   - Credential 'kubeconfig-staging' e 'kubeconfig-production' (secret file)
 *   - Credential 'slack-webhook' (secret text)
 *   - SonarQube server configurado como 'SonarQube' nas global settings
 *   - Plugin: Pipeline, Docker Pipeline, Kubernetes CLI, JUnit, JaCoCo, SonarQube Scanner
 */
pipeline {
    agent {
        docker {
            image 'study/jenkins-agent:latest'
            // Socket do Docker do host — necessário para o agente fazer docker build/push
            args '-v /var/run/docker.sock:/var/run/docker.sock -u root'
        }
    }

    environment {
        SERVICE      = 'stream-service'
        PORT         = '8083'
        REGISTRY     = credentials('docker-registry')
        IMAGE_TAG    = "${BUILD_NUMBER}-${GIT_COMMIT.take(7)}"
        IMAGE_NAME   = "${REGISTRY_USR}/${SERVICE}:${IMAGE_TAG}"
        SONAR_HOST   = 'http://sonarqube:9000'
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                // Stash para stages que rodam em agentes separados (se necessário)
                stash name: 'source', includes: '**'
            }
        }

        stage('Build') {
            steps {
                dir('backend') {
                    sh 'mvn -pl stream-service -am package -DskipTests -T 1C -q'
                }
            }
        }

        stage('Test') {
            steps {
                dir('backend') {
                    sh 'mvn -pl stream-service -am test'
                }
            }
            post {
                always {
                    // Publica relatório JUnit para visualização no Jenkins
                    junit 'backend/stream-service/target/surefire-reports/*.xml'
                    // Cobertura JaCoCo (já configurado no pom.xml do stream-service)
                    jacoco(
                        execPattern: 'backend/stream-service/target/jacoco.exec',
                        classPattern: 'backend/stream-service/target/classes',
                        sourcePattern: 'backend/stream-service/src/main/java'
                    )
                }
            }
        }

        stage('Code Quality') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            mvn -pl stream-service sonar:sonar \
                                -Dsonar.projectKey=${SERVICE} \
                                -Dsonar.host.url=${SONAR_HOST}
                        """
                    }
                }
                // Quality Gate barra o pipeline se a cobertura ou bugs estiverem abaixo do limiar
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Security Scan') {
            steps {
                dir('backend') {
                    sh """
                        mvn -pl stream-service dependency-check:check \
                            -DfailBuildOnCVSS=7 \
                            -Dformats=HTML,JSON \
                            -q
                    """
                }
            }
            post {
                always {
                    publishHTML(target: [
                        reportDir: 'backend/stream-service/target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: 'OWASP Dependency Check'
                    ])
                }
            }
        }

        stage('Docker Build & Push') {
            steps {
                // context é backend/ porque o Dockerfile copia pom.xml de módulos irmãos
                sh """
                    docker build \
                        -t ${IMAGE_NAME} \
                        -f backend/stream-service/Dockerfile \
                        backend/
                    echo "${REGISTRY_PSW}" | docker login -u "${REGISTRY_USR}" --password-stdin
                    docker push ${IMAGE_NAME}
                """
            }
        }

        stage('Trivy Image Scan') {
            steps {
                // --exit-code 1 faz o stage falhar se houver CVE HIGH ou CRITICAL
                // Em projetos de estudo pode-se usar --exit-code 0 para apenas reportar
                sh """
                    trivy image \
                        --exit-code 1 \
                        --severity HIGH,CRITICAL \
                        --no-progress \
                        ${IMAGE_NAME}
                """
            }
        }

        stage('Deploy Staging') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-staging', variable: 'KUBECONFIG')]) {
                    sh """
                        export IMAGE_TAG=${IMAGE_TAG}
                        export REGISTRY=${REGISTRY_USR}
                        envsubst < k8s/stream-service/deployment.yaml | kubectl apply -n staging -f -
                        kubectl apply -n staging -f k8s/stream-service/service.yaml
                        kubectl apply -n staging -f k8s/stream-service/hpa.yaml
                        kubectl rollout status deployment/stream-service -n staging --timeout=120s
                    """
                }
            }
        }

        stage('Integration Tests') {
            steps {
                // Coleção Postman para smoke test do endpoint SSE em staging
                sh """
                    newman run tests/postman/stream-service.json \
                        --env-var "baseUrl=http://stream-service.staging.svc.cluster.local:8083" \
                        --reporters cli,junit \
                        --reporter-junit-export target/newman-results.xml
                """
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/newman-results.xml'
                }
            }
        }

        stage('Deploy Production') {
            // Só executa na branch main e requer aprovação manual
            when { branch 'main' }
            steps {
                input message: "Deploy ${SERVICE}:${IMAGE_TAG} em production?", ok: 'Deploy'
                withCredentials([file(credentialsId: 'kubeconfig-production', variable: 'KUBECONFIG')]) {
                    sh """
                        export IMAGE_TAG=${IMAGE_TAG}
                        export REGISTRY=${REGISTRY_USR}
                        envsubst < k8s/stream-service/deployment.yaml | kubectl apply -n production -f -
                        kubectl rollout status deployment/stream-service -n production --timeout=120s
                    """
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'backend/stream-service/target/*.jar', fingerprint: true
            // Notificação Slack — canal #deploys
            withCredentials([string(credentialsId: 'slack-webhook', variable: 'SLACK_URL')]) {
                sh """
                    STATUS="${currentBuild.currentResult}"
                    curl -s -X POST "${SLACK_URL}" \
                        -H 'Content-type: application/json' \
                        -d "{\\"text\\":\\"[${SERVICE}] Build #${BUILD_NUMBER} — ${STATUS} | ${GIT_BRANCH} | <${BUILD_URL}|Ver build>\\"}"
                """
            }
            cleanWs()
        }
    }
}
