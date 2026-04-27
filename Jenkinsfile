/*
 * Pipeline principal do monorepo — executa todos os serviços.
 * Usado para CI completo (merge em main) e releases.
 *
 * Para redeploy de um serviço específico sem rodar tudo,
 * use o pipeline isolado em jenkins/pipelines/<service>.Jenkinsfile.
 *
 * Stages paralelas reduzem o tempo total de CI:
 *   - Build: backend (mvn) + frontend (npm) simultâneos
 *   - Test: 4 serviços + frontend em paralelo
 *   - Docker: build + push de todas as imagens em paralelo
 *
 * Pré-requisitos no Jenkins (mesmos do pipeline isolado):
 *   - Credentials: docker-registry, kubeconfig-staging, kubeconfig-production, slack-webhook
 *   - SonarQube server 'SonarQube' configurado nas global settings
 *   - Plugins: Pipeline, Docker Pipeline, Kubernetes CLI, Parallel Test Executor,
 *              JUnit, JaCoCo, SonarQube Scanner, HTML Publisher, Blue Ocean
 */
pipeline {
    agent {
        docker {
            image 'study/jenkins-agent:latest'
            args '-v /var/run/docker.sock:/var/run/docker.sock -u root'
        }
    }

    environment {
        REGISTRY  = credentials('docker-registry')
        IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT.take(7)}"
        SONAR_HOST = 'http://sonarqube:9000'
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                // Stash compartilha o workspace entre stages que podem rodar em agentes distintos
                stash name: 'source', includes: '**'
            }
        }

        // ── BUILD ─────────────────────────────────────────────────────────────────
        stage('Build') {
            parallel {
                stage('Backend') {
                    steps {
                        dir('backend') {
                            // -T 1C: 1 thread por CPU — paralelismo interno do Maven Reactor
                            sh 'mvn clean package -DskipTests -T 1C -q'
                        }
                    }
                }
                stage('Frontend') {
                    steps {
                        dir('frontend') {
                            sh 'npm ci --prefer-offline'
                            sh 'npm run build'
                        }
                    }
                }
            }
        }

        // ── TEST ──────────────────────────────────────────────────────────────────
        stage('Test') {
            parallel {
                stage('user-service') {
                    steps {
                        dir('backend') { sh 'mvn -pl user-service -am test' }
                    }
                    post {
                        always {
                            junit 'backend/user-service/target/surefire-reports/*.xml'
                            jacoco execPattern: 'backend/user-service/target/jacoco.exec',
                                   classPattern: 'backend/user-service/target/classes',
                                   sourcePattern: 'backend/user-service/src/main/java'
                        }
                    }
                }
                stage('auth-service') {
                    steps {
                        dir('backend') { sh 'mvn -pl auth-service -am test' }
                    }
                    post {
                        always { junit 'backend/auth-service/target/surefire-reports/*.xml' }
                    }
                }
                stage('stream-service') {
                    steps {
                        dir('backend') { sh 'mvn -pl stream-service -am test' }
                    }
                    post {
                        always { junit 'backend/stream-service/target/surefire-reports/*.xml' }
                    }
                }
                stage('api-gateway') {
                    steps {
                        dir('backend') { sh 'mvn -pl api-gateway -am test' }
                    }
                    post {
                        always { junit allowEmptyResults: true, testResults: 'backend/api-gateway/target/surefire-reports/*.xml' }
                    }
                }
                stage('Frontend Tests') {
                    steps {
                        dir('frontend') {
                            sh 'npm test -- --coverage --watchAll=false --ci'
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'frontend/coverage/junit.xml'
                        }
                    }
                }
            }
        }

        // ── CODE QUALITY ──────────────────────────────────────────────────────────
        stage('Code Quality') {
            steps {
                dir('backend') {
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            mvn sonar:sonar \
                                -Dsonar.projectKey=study \
                                -Dsonar.host.url=${SONAR_HOST}
                        """
                    }
                }
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ── SECURITY ──────────────────────────────────────────────────────────────
        stage('Security Scan') {
            parallel {
                stage('OWASP Backend') {
                    steps {
                        dir('backend') {
                            sh 'mvn dependency-check:aggregate -DfailBuildOnCVSS=7 -Dformats=HTML,JSON -q'
                        }
                    }
                    post {
                        always {
                            publishHTML(target: [
                                reportDir: 'backend/target',
                                reportFiles: 'dependency-check-report.html',
                                reportName: 'OWASP Backend'
                            ])
                        }
                    }
                }
                stage('npm audit') {
                    steps {
                        dir('frontend') {
                            sh 'npm audit --audit-level=high'
                        }
                    }
                }
            }
        }

        // ── DOCKER BUILD & PUSH ───────────────────────────────────────────────────
        stage('Docker Build & Push') {
            steps {
                sh "echo '${REGISTRY_PSW}' | docker login -u '${REGISTRY_USR}' --password-stdin"
                parallel(
                    'user-service': {
                        sh """
                            docker build -t ${REGISTRY_USR}/user-service:${IMAGE_TAG} \
                                -f backend/user-service/Dockerfile backend/
                            docker push ${REGISTRY_USR}/user-service:${IMAGE_TAG}
                        """
                    },
                    'auth-service': {
                        sh """
                            docker build -t ${REGISTRY_USR}/auth-service:${IMAGE_TAG} \
                                -f backend/auth-service/Dockerfile backend/
                            docker push ${REGISTRY_USR}/auth-service:${IMAGE_TAG}
                        """
                    },
                    'stream-service': {
                        sh """
                            docker build -t ${REGISTRY_USR}/stream-service:${IMAGE_TAG} \
                                -f backend/stream-service/Dockerfile backend/
                            docker push ${REGISTRY_USR}/stream-service:${IMAGE_TAG}
                        """
                    },
                    'api-gateway': {
                        sh """
                            docker build -t ${REGISTRY_USR}/api-gateway:${IMAGE_TAG} \
                                -f backend/api-gateway/Dockerfile backend/
                            docker push ${REGISTRY_USR}/api-gateway:${IMAGE_TAG}
                        """
                    }
                )
            }
        }

        // ── TRIVY IMAGE SCAN ──────────────────────────────────────────────────────
        stage('Trivy Image Scan') {
            steps {
                parallel(
                    'user-service':    { sh "trivy image --exit-code 1 --severity HIGH,CRITICAL --no-progress ${REGISTRY_USR}/user-service:${IMAGE_TAG}" },
                    'auth-service':    { sh "trivy image --exit-code 1 --severity HIGH,CRITICAL --no-progress ${REGISTRY_USR}/auth-service:${IMAGE_TAG}" },
                    'stream-service':  { sh "trivy image --exit-code 1 --severity HIGH,CRITICAL --no-progress ${REGISTRY_USR}/stream-service:${IMAGE_TAG}" },
                    'api-gateway':     { sh "trivy image --exit-code 1 --severity HIGH,CRITICAL --no-progress ${REGISTRY_USR}/api-gateway:${IMAGE_TAG}" }
                )
            }
        }

        // ── DEPLOY STAGING ────────────────────────────────────────────────────────
        stage('Deploy Staging') {
            steps {
                withCredentials([file(credentialsId: 'kubeconfig-staging', variable: 'KUBECONFIG')]) {
                    sh """
                        export IMAGE_TAG=${IMAGE_TAG}
                        export REGISTRY=${REGISTRY_USR}

                        kubectl apply -f k8s/namespace.yaml
                        kubectl apply -f k8s/configmap.yaml

                        for svc in user-service auth-service stream-service api-gateway; do
                            envsubst < k8s/\${svc}/deployment.yaml | kubectl apply -n staging -f -
                            kubectl apply -n staging -f k8s/\${svc}/service.yaml
                            kubectl apply -n staging -f k8s/\${svc}/hpa.yaml
                        done

                        for svc in user-service auth-service stream-service api-gateway; do
                            kubectl rollout status deployment/\${svc} -n staging --timeout=120s
                        done
                    """
                }
            }
        }

        // ── INTEGRATION TESTS ─────────────────────────────────────────────────────
        stage('Integration Tests') {
            steps {
                sh """
                    newman run tests/postman/study-api.json \
                        --env-var "baseUrl=http://api-gateway.staging.svc.cluster.local:8080" \
                        --reporters cli,junit \
                        --reporter-junit-export target/newman-results.xml
                """
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'target/newman-results.xml' }
            }
        }

        // ── DEPLOY PRODUCTION ─────────────────────────────────────────────────────
        stage('Deploy Production') {
            // Aprovação manual obrigatória — só na branch main
            when { branch 'main' }
            steps {
                input message: "Deploy build #${BUILD_NUMBER} (${IMAGE_TAG}) em production?", ok: 'Deploy'
                withCredentials([file(credentialsId: 'kubeconfig-production', variable: 'KUBECONFIG')]) {
                    sh """
                        export IMAGE_TAG=${IMAGE_TAG}
                        export REGISTRY=${REGISTRY_USR}

                        kubectl apply -f k8s/namespace.yaml
                        kubectl apply -f k8s/configmap.yaml

                        for svc in user-service auth-service stream-service api-gateway; do
                            envsubst < k8s/\${svc}/deployment.yaml | kubectl apply -n production -f -
                        done

                        for svc in user-service auth-service stream-service api-gateway; do
                            kubectl rollout status deployment/\${svc} -n production --timeout=120s
                        done
                    """
                }
            }
        }
    }

    // ── POST ──────────────────────────────────────────────────────────────────────
    post {
        always {
            // Arquiva JARs para rastreabilidade (qual JAR gerou qual imagem)
            archiveArtifacts artifacts: 'backend/**/target/*.jar', fingerprint: true
            withCredentials([string(credentialsId: 'slack-webhook', variable: 'SLACK_URL')]) {
                sh """
                    STATUS="${currentBuild.currentResult}"
                    COLOR=\$([ "\$STATUS" = "SUCCESS" ] && echo "good" || echo "danger")
                    curl -s -X POST "${SLACK_URL}" \
                        -H 'Content-type: application/json' \
                        -d "{\\"attachments\\":[{\\"color\\":\\"\$COLOR\\",\\"text\\":\\"[monorepo] Build #${BUILD_NUMBER} — \$STATUS | ${GIT_BRANCH} | ${IMAGE_TAG} | <${BUILD_URL}|Ver build>\\"}]}"
                """
            }
            // Libera espaço no agente — crítico para agentes estáticos com disco limitado
            cleanWs()
        }
    }
}
