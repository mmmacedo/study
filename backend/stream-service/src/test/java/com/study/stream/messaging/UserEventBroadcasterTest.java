package com.study.stream.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.stream.event.UserCreatedEvent;
import com.study.stream.event.UserDeactivatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Testa UserEventBroadcaster sem Spring — instanciação direta.
 * StepVerifier garante que o Consumer emite o evento correto no Flux SSE.
 *
 * Por que sem Spring:
 *   O broadcaster é um POJO com ObjectMapper injetado. Não precisa de contexto
 *   Spring para ser testado. Sem @SpringBootTest: teste mais rápido e isolado.
 */
class UserEventBroadcasterTest {

    private UserEventBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new UserEventBroadcaster(new ObjectMapper());
    }

    @Test
    void userCreated_emitsUserCreatedSse() {
        var event = new UserCreatedEvent(UUID.randomUUID(), "Ana", "ana@test.com", "USER", "pass");

        // take(1): limita o Flux a 1 elemento e completa — sem isso StepVerifier
        // esperaria indefinidamente (o Sink nunca completa sozinho).
        StepVerifier.create(broadcaster.asFlux().take(1))
                .then(() -> broadcaster.userCreated().accept(event))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("user-created");
                    assertThat(sse.data()).contains("ana@test.com");
                    assertThat(sse.data()).contains(event.userId().toString());
                })
                .verifyComplete();
    }

    @Test
    void userDeactivated_emitsUserDeactivatedSse() {
        var event = new UserDeactivatedEvent(UUID.randomUUID(), "bob@test.com");

        StepVerifier.create(broadcaster.asFlux().take(1))
                .then(() -> broadcaster.userDeactivated().accept(event))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("user-deactivated");
                    assertThat(sse.data()).contains("bob@test.com");
                    assertThat(sse.data()).contains(event.userId().toString());
                })
                .verifyComplete();
    }

}
