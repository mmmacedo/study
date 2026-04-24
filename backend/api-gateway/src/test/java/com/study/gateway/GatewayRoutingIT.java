package com.study.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/*
 * Testes de integração do api-gateway
 * =====================================
 * Valida as responsabilidades que pertencem ao gateway (não aos serviços downstream):
 *   1. Endpoints públicos são acessíveis sem JWT
 *   2. Rotas protegidas rejeitam requests sem JWT (HTTP 401)
 *   3. FallbackController retorna 503 corretamente
 *
 * O que NÃO testamos aqui:
 *   - Roteamento real para user-service/auth-service/stream-service
 *     (esses serviços não estão rodando nos testes — é responsabilidade de
 *     testes E2E com Newman/Postman contra o ambiente de staging)
 *   - Rate limiting em carga real (exigiria testes de performance separados)
 *
 * Stack dos testes:
 *   - Redis real via Testcontainers (necessário para o RequestRateLimiter)
 *   - Keycloak NÃO necessário: jwk-set-uri é lazy (só chamado ao validar JWT)
 *     e nossos testes não enviam JWT válido
 *   - WebTestClient (variante reativa do MockMvc para WebFlux)
 *
 * RANDOM_PORT: evita conflito de porta 8080 com outros serviços no CI.
 * O WebTestClient é auto-configurado para a porta aleatória.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class GatewayRoutingIT {

    /*
     * Redis via Testcontainers
     * =========================
     * O RequestRateLimiter exige Redis para armazenar o estado do token bucket.
     * Sem Redis, o contexto Spring não sobe (falha ao conectar).
     *
     * GenericContainer em vez de RedisContainer:
     *   Não há módulo testcontainers:redis no classpath — usamos a imagem Docker
     *   diretamente. @DynamicPropertySource injeta o host e a porta aleatória
     *   na configuração do Spring antes do contexto subir.
     *
     * static: o container sobe uma vez e é compartilhado por todos os testes
     * da classe (mais eficiente que subir/descer a cada @Test).
     */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /*
     * @DynamicPropertySource injeta propriedades calculadas em runtime
     * (host e porta aleatória do container) antes do ApplicationContext subir.
     * Sem isso, o Spring tentaria conectar no Redis em localhost:6379 e falharia.
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpointIsPublic() {
        /*
         * /actuator/health deve ser acessível sem autenticação.
         * O Kubernetes usa este endpoint para liveness e readiness probes —
         * se exigisse JWT, os probes falhariam e o pod seria reiniciado infinitamente.
         */
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedRouteRequiresJwt() {
        /*
         * GET /api/users sem Authorization header deve retornar 401 Unauthorized.
         * O Spring Security intercede ANTES do roteamento — o request nunca chega
         * ao user-service. Isso confirma que a SecurityWebFilterChain está correta.
         *
         * O circuit breaker não é acionado aqui: a rejeição acontece na camada
         * de segurança, não na tentativa de conexão com o upstream.
         */
        webTestClient.get()
                .uri("/api/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void fallbackEndpointIsPublic() {
        /*
         * GET /fallback/user-service deve retornar 503 com ProblemDetail.
         * Validamos dois comportamentos em um teste:
         *   1. O endpoint é público (permitAll no SecurityConfig) — sem 401
         *   2. O FallbackController responde corretamente com SERVICE_UNAVAILABLE
         *
         * Em produção, este endpoint só é chamado internamente via forward:
         * pelo circuit breaker. Mas estar acessível diretamente é inofensivo
         * (apenas informa ao cliente que o serviço está fora).
         */
        webTestClient.get()
                .uri("/fallback/user-service")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.service").isEqualTo("user-service");
    }
}
