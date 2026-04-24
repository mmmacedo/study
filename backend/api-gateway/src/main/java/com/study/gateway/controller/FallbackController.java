package com.study.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

/*
 * FallbackController — respostas quando o circuit breaker está aberto
 * ====================================================================
 * Quando um serviço downstream falha repetidamente (failure-rate-threshold
 * ultrapassado), o Resilience4j abre o circuit breaker e redireciona as
 * requisições para o fallbackUri configurado em application.yml:
 *
 *   fallbackUri: forward:/fallback/<service-name>
 *
 * O gateway faz um "forward" interno para este controller — o cliente
 * recebe 503 Service Unavailable com um corpo explicativo (RFC 7807).
 *
 * Por que @RequestMapping sem method?
 *   O circuit breaker preserva o método HTTP original do cliente (GET, POST,
 *   DELETE, etc.). Se só mapeássemos @GetMapping, um POST que aciona o fallback
 *   resultaria em 405 Method Not Allowed em vez de 503. @RequestMapping sem
 *   restrição de método aceita qualquer verbo.
 *
 * Por que retornar Mono<ResponseEntity>?
 *   O api-gateway é reativo (WebFlux). Controllers precisam retornar tipos
 *   reativos ou serão chamados em um thread de evento (não bloqueante).
 *   Mono.just() cria um Mono que emite imediatamente o valor — adequado
 *   para respostas construídas em memória sem I/O.
 */
@Slf4j
@RestController
public class FallbackController {

    @RequestMapping("/fallback/user-service")
    public Mono<ResponseEntity<ProblemDetail>> userServiceFallback() {
        return buildFallback("user-service");
    }

    @RequestMapping("/fallback/auth-service")
    public Mono<ResponseEntity<ProblemDetail>> authServiceFallback() {
        return buildFallback("auth-service");
    }

    @RequestMapping("/fallback/stream-service")
    public Mono<ResponseEntity<ProblemDetail>> streamServiceFallback() {
        return buildFallback("stream-service");
    }

    private Mono<ResponseEntity<ProblemDetail>> buildFallback(String service) {
        log.warn("Circuit breaker open — service unavailable: {}", service);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "O serviço " + service + " está temporariamente indisponível. Tente novamente em alguns instantes."
        );
        problem.setType(URI.create("/errors/service-unavailable"));
        problem.setTitle("Serviço Indisponível");
        problem.setProperty("service", service);

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(problem));
    }
}
