package com.study.stream.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.stream.event.UserCreatedEvent;
import com.study.stream.event.UserDeactivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.function.Consumer;

/*
 * UserEventBroadcaster — ponte entre Kafka (Spring Cloud Stream) e SSE.
 *
 * Sinks.many().multicast().onBackpressureBuffer():
 *   - "many": emite múltiplos eventos (não just one)
 *   - "multicast": N subscribers simultâneos (um por cliente SSE conectado)
 *   - "onBackpressureBuffer": bufferiza eventos quando downstream está lento;
 *     thread-safe sem synchronized — o Sinks garante serializabilidade das emissões.
 *
 * Os Consumer<T> são beans Spring nomeados "userCreated" e "userDeactivated".
 * O Spring Cloud Stream lê spring.cloud.function.definition e cria os bindings:
 *   userCreated     → tópico user-created     (userCreated-in-0)
 *   userDeactivated → tópico user-deactivated  (userDeactivated-in-0)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UserEventBroadcaster {

    private final ObjectMapper objectMapper;

    // autoCancel = false: o Sink não termina quando todos os subscribers cancelam.
    // Sem isso, quando um cliente SSE desconecta (último subscriber), o Sink auto-completa
    // e novos clientes que conectarem receberão onComplete() imediatamente sem eventos.
    private final Sinks.Many<ServerSentEvent<String>> eventSink =
            Sinks.many().multicast().onBackpressureBuffer(256, false);

    public Flux<ServerSentEvent<String>> asFlux() {
        return eventSink.asFlux();
    }

    @Bean
    public Consumer<UserCreatedEvent> userCreated() {
        return event -> {
            log.info("Broadcasting UserCreatedEvent userId={}", event.userId());
            emit("user-created", event);
        };
    }

    @Bean
    public Consumer<UserDeactivatedEvent> userDeactivated() {
        return event -> {
            log.info("Broadcasting UserDeactivatedEvent userId={}", event.userId());
            emit("user-deactivated", event);
        };
    }

    private void emit(String eventType, Object payload) {
        try {
            var sse = ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
            eventSink.tryEmitNext(sse);
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar evento {}", eventType, e);
        }
    }
}
