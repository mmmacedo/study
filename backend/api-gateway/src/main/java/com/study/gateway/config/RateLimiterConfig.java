package com.study.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class RateLimiterConfig {

    /*
     * KeyResolver — chave de particionamento do rate limiter
     * =======================================================
     * O RequestRateLimiter do Spring Cloud Gateway precisa de uma chave para
     * identificar "quem está fazendo a requisição" e aplicar o limite por entidade.
     *
     * Estratégia adotada (duas camadas):
     *
     *   1. Request autenticado (JWT presente e válido):
     *      Chave = claim "sub" (subject) do JWT = ID do usuário no Keycloak.
     *      Cada usuário tem seu próprio bucket de tokens — um usuário não consome
     *      o limite de outro. Resiliente a múltiplos IPs do mesmo usuário (mobile,
     *      VPN, múltiplos dispositivos).
     *
     *   2. Request sem JWT (ex: /auth/** — login e refresh):
     *      Chave = IP remoto do cliente.
     *      Protege os endpoints públicos de ataques de força bruta ou flood
     *      (ex: tentativas de login em massa).
     *      Limitação: proxies/NATs compartilham IP — mas é o melhor disponível
     *      sem autenticação.
     *
     * Por que não usar apenas IP para todos?
     *   Um usuário legítimo em uma rede corporativa (NAT) compartilha o mesmo
     *   IP com centenas de colegas — o rate limiter bloquearia todos juntos.
     *   JWT sub é a granularidade correta para requests autenticados.
     *
     * Configuração de limites: application.yml (redis-rate-limiter.replenishRate, burstCapacity)
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> principal.getName())
                .defaultIfEmpty(
                        Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                                .map(addr -> addr.getAddress().getHostAddress())
                                .orElse("anonymous")
                );
    }
}
