package com.study.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gerencia usuários no Keycloak via Admin REST API usando client credentials.
 *
 * Fluxo: auth-service autentica como "auth-admin-client" (client_credentials grant)
 * e usa o access_token resultante para chamar os endpoints de administração.
 *
 * Token caching: evita uma chamada extra ao Keycloak em cada operação.
 * volatile: garante visibilidade entre virtual threads sem synchronization completo.
 * O pior caso de race condition é duas threads obtendo um token novo ao mesmo tempo
 * — aceitável; o segundo token simplesmente sobrescreve o primeiro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService implements KeycloakAdminOperations {

    private final RestClient keycloakRestClient;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String adminClientId;

    @Value("${keycloak.admin.client-secret}")
    private String adminClientSecret;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    // =========================================================================
    // API pública
    // =========================================================================

    /**
     * Cria um usuário no Keycloak com o mesmo UUID já persistido no user-service.
     * Keycloak aceita o campo "id" no body do POST /admin/realms/{realm}/users.
     *
     * Idempotente: 409 Conflict (usuário já existe) é silenciado com WARN.
     */
    public void createUser(UUID userId, String username, String email, String role, String temporaryPassword) {
        log.info("Criando usuário no Keycloak userId={} email={}", userId, email);
        String token = adminToken();

        try {
            keycloakRestClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "id", userId.toString(),
                            "username", username,
                            "email", email,
                            "enabled", true,
                            "emailVerified", true,
                            "credentials", List.of(Map.of(
                                    "type", "password",
                                    "value", temporaryPassword,
                                    "temporary", true
                            ))
                    ))
                    .retrieve()
                    .toBodilessEntity();

            assignRealmRole(userId, role, token);
            log.info("Usuário criado no Keycloak userId={}", userId);

        } catch (HttpClientErrorException.Conflict e) {
            log.warn("Usuário já existe no Keycloak userId={} — ignorando", userId);
        }
    }

    /**
     * Desabilita um usuário no Keycloak via PUT.
     * Idempotente: 404 (usuário não encontrado) é silenciado com WARN.
     */
    public void disableUser(UUID userId) {
        log.info("Desabilitando usuário no Keycloak userId={}", userId);
        String token = adminToken();

        try {
            keycloakRestClient.put()
                    .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", false))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Usuário desabilitado no Keycloak userId={}", userId);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Usuário não encontrado no Keycloak userId={} — ignorando", userId);
        }
    }

    // =========================================================================
    // Internos
    // =========================================================================

    private void assignRealmRole(UUID userId, String roleName, String token) {
        // Busca o objeto de role pelo nome para obter o id (necessário no body)
        var roleRep = keycloakRestClient.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, roleName)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(RoleRepresentation.class);

        if (roleRep == null) {
            log.warn("Role {} não encontrada no Keycloak — atribuição ignorada", roleName);
            return;
        }

        keycloakRestClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleRep))
                .retrieve()
                .toBodilessEntity();
    }

    private String adminToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", adminClientId);
        form.add("client_secret", adminClientSecret);

        var response = keycloakRestClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(AdminTokenResponse.class);

        cachedToken = response.accessToken();
        // Renova 30s antes do vencimento para evitar usar token expirado
        tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn() - 30);
        return cachedToken;
    }

    private record AdminTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {}

    private record RoleRepresentation(String id, String name) {}
}
