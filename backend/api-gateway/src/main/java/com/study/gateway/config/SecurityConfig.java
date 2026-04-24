package com.study.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /*
     * SecurityWebFilterChain (WebFlux) vs SecurityFilterChain (MVC)
     * ===============================================================
     * O api-gateway usa WebFlux — a cadeia de filtros é reativa.
     * ServerHttpSecurity em vez de HttpSecurity.
     * SecurityWebFilterChain em vez de SecurityFilterChain.
     *
     * CSRF desabilitado:
     *   APIs REST stateless que usam JWT não precisam de proteção CSRF.
     *   CSRF protege contra ataques onde um site malicioso usa os cookies
     *   do usuário para fazer requests autenticados — mas JWT no header
     *   Authorization não é enviado automaticamente pelo browser.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        // Kubernetes liveness/readiness probes — sem autenticação
                        .pathMatchers("/actuator/health/**").permitAll()
                        // Endpoints de autenticação — o cliente ainda não tem JWT aqui
                        .pathMatchers("/auth/**").permitAll()
                        // Fallbacks do circuit breaker — acessados internamente via forward:
                        .pathMatchers("/fallback/**").permitAll()
                        // Todos os outros endpoints exigem JWT válido
                        .anyExchange().authenticated()
                )
                /*
                 * OAuth2 Resource Server com JWT — variante reativa
                 * ===================================================
                 * O fluxo:
                 *   1. Request chega com Authorization: Bearer <token>
                 *   2. ReactiveJwtDecoder busca as chaves públicas via JWKS endpoint
                 *      (jwk-set-uri no application.yml — lazy, só na primeira chamada)
                 *   3. Verifica a assinatura e as claims (exp, iss, etc.)
                 *   4. reactiveJwtAuthenticationConverter() extrai roles de realm_access.roles
                 *   5. ReactiveSecurityContextHolder é populado com o Authentication
                 *
                 * Se o token for inválido ou ausente: HTTP 401 antes de qualquer roteamento.
                 */
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter()))
                )
                .build();
    }

    /*
     * ReactiveJwtAuthenticationConverter — variante reativa do JwtAuthenticationConverter
     * =====================================================================================
     * Mesmo papel que o converter do user-service, mas retorna Flux em vez de List.
     * Necessário porque o WebFlux usa publisher (Flux/Mono) em toda a cadeia reativa —
     * um método síncrono bloquearia a event loop.
     *
     * Keycloak publica roles em realm_access.roles:
     *   "ADMIN" → SimpleGrantedAuthority("ROLE_ADMIN")
     *   "USER"  → SimpleGrantedAuthority("ROLE_USER")
     *
     * O gateway atualmente só verifica .anyExchange().authenticated() — não checa
     * roles específicas. Mas extrair as authorities corretamente garante que o
     * Authentication propagado para os filtros internos (ex: logs de auditoria)
     * reflita as roles reais do usuário.
     */
    @Bean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter() {
        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return Flux.empty();

            @SuppressWarnings("unchecked")
            var roles = (List<String>) realmAccess.get("roles");
            if (roles == null) return Flux.empty();

            return Flux.fromIterable(roles)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
