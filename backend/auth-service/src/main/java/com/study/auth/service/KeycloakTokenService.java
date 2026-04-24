package com.study.auth.service;

import com.study.auth.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class KeycloakTokenService {

    private final RestClient keycloakRestClient;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    /*
     * Delega a autenticação por senha ao endpoint de token do Keycloak.
     *
     * Resource Owner Password Credentials Grant (ROPC):
     *   O cliente envia username + password diretamente para o servidor de autorização.
     *   Uso aceitável em: APIs internas, CLIs, ambientes de estudo onde o cliente
     *   é confiável. Em aplicações públicas preferir Authorization Code Flow.
     *
     * Keycloak retorna HTTP 400 "Invalid user credentials" se a senha estiver errada.
     * RestClient lança RestClientResponseException, capturada no controller.
     */
    public TokenResponse token(String username, String password) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", username);
        form.add("password", password);

        return keycloakRestClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    /*
     * Troca um refresh_token por um novo par access_token + refresh_token.
     *
     * O Keycloak invalida o refresh_token usado e emite um novo.
     * Rotation de refresh tokens é habilitada por padrão no Keycloak.
     */
    public TokenResponse refresh(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);

        return keycloakRestClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    /*
     * Invalida a sessão no Keycloak via logout endpoint.
     *
     * Envia o refresh_token para revogar — o access_token expira naturalmente
     * (JWTs são stateless; revogação só se aplica ao refresh_token no Keycloak).
     * O cliente deve descartar o access_token localmente após esta chamada.
     */
    public void logout(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);

        keycloakRestClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/logout", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
