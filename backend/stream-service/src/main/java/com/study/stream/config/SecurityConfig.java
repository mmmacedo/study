package com.study.stream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/*
 * SecurityConfig para WebFlux usa ServerHttpSecurity (não HttpSecurity do MVC).
 *
 * Diferenças-chave em relação ao MVC:
 *   - @EnableWebFluxSecurity (não @EnableWebSecurity)
 *   - SecurityWebFilterChain (não SecurityFilterChain)
 *   - ReactiveJwtAuthenticationConverter (não JwtAuthenticationConverter)
 *   - Retorna Flux<GrantedAuthority> (não Collection<GrantedAuthority>)
 *   - CSRF: desabilitado (API REST stateless — tokens JWT, sem cookies de sessão)
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new ReactiveJwtAuthenticationConverter();
        // Extrai roles do claim realm_access.roles do Keycloak e prefixa com "ROLE_"
        // para compatibilidade com hasRole() do Spring Security.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return Flux.empty();
            @SuppressWarnings("unchecked")
            var roles = (List<String>) realmAccess.get("roles");
            if (roles == null) return Flux.empty();
            return Flux.fromIterable(roles)
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r));
        });
        return converter;
    }
}
