package com.study.stream.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.stream.event.UserCreatedEvent;
import com.study.stream.event.UserDeactivatedEvent;
import com.study.stream.messaging.UserEventBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/*
 * Teste de integração: Spring Boot com servidor real (RANDOM_PORT) + test binder em memória.
 *
 * Estratégia de testes:
 *   1. Endpoint HTTP: verifica apenas headers (status + content-type).
 *      O `:connected` inicial é enviado assim que o cliente conecta, forçando o flush
 *      dos headers HTTP. Não tentamos consumir o body: returnResult(String.class) com
 *      text/event-stream usa ServerSentEventHttpMessageReader, que descarta comentários
 *      SSE (`:connected` não tem `data:`) → Flux vazio → StepVerifier nunca completa.
 *
 *   2. Broadcaster (binding InputDestination → Consumer → Sinks.Many): testado diretamente.
 *      Subscrever ao broadcaster.asFlux() ANTES de enviar o evento (via .then()) garante
 *      que o StepVerifier está registrado quando o evento é emitido — sem race condition.
 *
 * @MockitoBean ReactiveJwtDecoder:
 *   Evita dependência do Keycloak. Quando nenhum header Authorization é enviado, o Spring
 *   Security rejeita com 401 antes de invocar o decoder.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
class StreamControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserEventBroadcaster broadcaster;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();

        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "test-user")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));
    }

    @Test
    void sseEndpoint_authenticatedRequest_returnsOkAndEventStream() {
        // Verifica status + content-type. Não consome o body: returnResult(String.class) com
        // text/event-stream usa o ServerSentEventHttpMessageReader, que descarta comentários SSE
        // (`:connected` é um comentário — sem `data:`) e nunca emite elementos, causando hang.
        // O fato de exchange() retornar sem timeout prova que o header flush ocorreu
        // (graças ao comentário :connected que força o Netty a enviar os headers HTTP).
        webTestClient
                .get().uri("/stream/events")
                .header("Authorization", "Bearer test-token")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void userCreatedEvent_deliveredViaBroadcaster() throws Exception {
        var event = new UserCreatedEvent(UUID.randomUUID(), "Ana", "ana@test.com", "USER", "pass");

        // .then() garante que o evento é enviado APÓS a subscrição do StepVerifier.
        // Sem isso, o evento poderia ser emitido ao Sinks antes de haver subscribers,
        // sendo descartado (multicast não faz replay de eventos passados).
        StepVerifier.create(broadcaster.asFlux().take(1))
                .then(() -> {
                    try {
                        inputDestination.send(
                                new GenericMessage<>(objectMapper.writeValueAsBytes(event)),
                                "user-created"
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("user-created");
                    assertThat(sse.data()).contains("ana@test.com");
                    assertThat(sse.data()).contains(event.userId().toString());
                })
                .verifyComplete();
    }

    @Test
    void userDeactivatedEvent_deliveredViaBroadcaster() throws Exception {
        var event = new UserDeactivatedEvent(UUID.randomUUID(), "bob@test.com");

        StepVerifier.create(broadcaster.asFlux().take(1))
                .then(() -> {
                    try {
                        inputDestination.send(
                                new GenericMessage<>(objectMapper.writeValueAsBytes(event)),
                                "user-deactivated"
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("user-deactivated");
                    assertThat(sse.data()).contains("bob@test.com");
                    assertThat(sse.data()).contains(event.userId().toString());
                })
                .verifyComplete();
    }

    @Test
    void sseEndpoint_requiresAuthentication() {
        webTestClient
                .get().uri("/stream/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
