package com.study.stream.controller;

import com.study.stream.messaging.UserEventBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
public class StreamController {

    private final UserEventBroadcaster broadcaster;

    /*
     * Endpoint SSE: cada GET abre uma conexão HTTP persistente (text/event-stream).
     *
     * O comentário "connected" inicial é obrigatório por dois motivos:
     *   1. Netty/Spring WebFlux só envia os headers HTTP quando o primeiro dado é escrito.
     *      Sem o comentário, clientes ficam aguardando headers indefinidamente se não houver
     *      eventos pendentes — isso causa timeout nos testes e quebra o EventSource do browser.
     *   2. Confirma ao cliente que a conexão foi estabelecida com sucesso.
     *
     * O api-gateway tem timeout de 30s configurado para esta rota — clientes
     * precisam reconectar periodicamente (comportamento padrão do EventSource do browser).
     */
    @GetMapping(value = "/stream/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> events() {
        var connected = ServerSentEvent.<String>builder().comment("connected").build();
        return Flux.concat(Flux.just(connected), broadcaster.asFlux());
    }
}
