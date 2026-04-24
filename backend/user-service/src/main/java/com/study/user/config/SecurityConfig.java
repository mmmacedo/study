package com.study.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;

/**
 * Configuração de segurança do user-service.
 *
 * @EnableMethodSecurity:
 *   OBRIGATÓRIA para que @PreAuthorize funcione. Sem esta anotação, todas as
 *   anotações @PreAuthorize nos controllers são silenciosamente ignoradas —
 *   os endpoints ficam acessíveis sem autenticação, sem qualquer erro ou aviso.
 *   É um erro clássico e difícil de detectar sem testes de segurança.
 *
 *   Habilita três mecanismos de autorização a nível de método:
 *     @PreAuthorize  → avaliado ANTES de chamar o método (mais comum)
 *     @PostAuthorize → avaliado DEPOIS, com acesso ao valor de retorno
 *     @Secured       → versão legada, sem SpEL — prefira @PreAuthorize
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * SecurityFilterChain define as regras de segurança HTTP.
     *
     * Por que definir explicitamente em vez de deixar o auto-configure?
     *   O Spring Security auto-configura uma cadeia padrão, mas ela não sabe
     *   quais endpoints são públicos (health) vs protegidos. Declarar explicitamente
     *   garante que qualquer endpoint novo seja protegido por padrão (fail-safe).
     *
     * @param http construtor de configuração fornecido pelo Spring Security
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF desabilitado: REST API stateless com JWT não precisa de CSRF.
            // CSRF protege contra ataques baseados em cookies de sessão — como usamos
            // JWT no header Authorization (não cookie), o ataque não é aplicável.
            // Sem desabilitar: POST/PUT/DELETE em testes retornam 403 sem token CSRF.
            .csrf(csrf -> csrf.disable())

            // Stateless: sem sessão HTTP. Cada request é autenticado pelo JWT.
            // STATELESS garante que o Spring Security nunca crie nem use HttpSession —
            // comportamento correto para microserviços por trás de um api-gateway.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Probes do Kubernetes precisam funcionar sem token.
                // /actuator/health/liveness e /actuator/health/readiness
                // são chamados pelo kubelet — sem capacidade de enviar JWT.
                .requestMatchers("/actuator/health/**").permitAll()

                // Todo o resto exige um JWT válido.
                // A validação da assinatura e expiração é feita pelo filtro
                // oauth2ResourceServer abaixo — aqui só verificamos autenticação.
                .anyRequest().authenticated()
            )

            // Configura o serviço como OAuth2 Resource Server com validação de JWT.
            //
            // Fluxo de validação:
            //   1. Filtro extrai o token do header Authorization: Bearer <token>
            //   2. Spring busca as chaves públicas no jwk-set-uri (Keycloak)
            //   3. Verifica assinatura, expiração e issuer do JWT
            //   4. jwtAuthenticationConverter() extrai roles de realm_access.roles
            //   5. @PreAuthorize pode então checar as roles mapeadas
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Converte claims do JWT do Keycloak em GrantedAuthority do Spring Security.
     *
     * Por que é necessário:
     *   O Spring Security por padrão lê roles da claim "scope" ou "scp" do JWT.
     *   O Keycloak, porém, publica roles na claim aninhada "realm_access.roles":
     *
     *     {
     *       "realm_access": {
     *         "roles": ["USER", "ADMIN", "offline_access", "uma_authorization"]
     *       }
     *     }
     *
     *   Sem este converter, @PreAuthorize("hasRole('ADMIN')") nunca é satisfeito —
     *   o Spring Security simplesmente não encontra a role no lugar errado.
     *
     * Mapeamento aplicado:
     *   "ADMIN" → SimpleGrantedAuthority("ROLE_ADMIN")
     *   "USER"  → SimpleGrantedAuthority("ROLE_USER")
     *
     *   O prefixo "ROLE_" é adicionado porque hasRole('ADMIN') no SpEL
     *   busca exatamente "ROLE_ADMIN" na lista de authorities.
     *   hasAuthority('ADMIN') buscaria sem prefixo — prefira hasRole para clareza.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return List.of();

            @SuppressWarnings("unchecked")
            var roles = (List<String>) realmAccess.get("roles");
            if (roles == null) return List.of();

            return roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });
        return converter;
    }
}
