package com.study.auth.controller;

import com.study.auth.domain.EventType;
import com.study.auth.dto.IntrospectResponse;
import com.study.auth.dto.RefreshRequest;
import com.study.auth.dto.TokenRequest;
import com.study.auth.dto.TokenResponse;
import com.study.auth.service.AuditLogService;
import com.study.auth.service.KeycloakTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final KeycloakTokenService keycloakTokenService;
    private final AuditLogService auditLogService;

    /*
     * POST /auth/token — público
     *
     * Delega a autenticação por senha ao Keycloak e retorna o par de tokens.
     * Registra o evento no audit log (success ou failure).
     */
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest req,
                                               HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        try {
            TokenResponse response = keycloakTokenService.token(req.username(), req.password());
            auditLogService.log(EventType.TOKEN, req.username(), ip, true, null);
            return ResponseEntity.ok(response);
        } catch (RestClientResponseException ex) {
            auditLogService.log(EventType.TOKEN, req.username(), ip, false, ex.getMessage());
            throw ex;
        }
    }

    /*
     * POST /auth/refresh — público
     *
     * Troca um refresh_token por um novo par de tokens.
     * Público porque o cliente pode não ter mais o access_token válido.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req,
                                                 HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        try {
            TokenResponse response = keycloakTokenService.refresh(req.refreshToken());
            auditLogService.log(EventType.REFRESH, null, ip, true, null);
            return ResponseEntity.ok(response);
        } catch (RestClientResponseException ex) {
            auditLogService.log(EventType.REFRESH, null, ip, false, ex.getMessage());
            throw ex;
        }
    }

    /*
     * POST /auth/logout — requer JWT
     *
     * Recebe o refresh_token no body e o revoga no Keycloak.
     * O access_token é stateless (JWT) e expira naturalmente — o cliente
     * deve descartá-lo localmente após esta chamada.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body,
                                       @AuthenticationPrincipal Jwt jwt,
                                       HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String subject = jwt.getSubject();
        String refreshToken = body.get("refresh_token");

        try {
            keycloakTokenService.logout(refreshToken);
            auditLogService.log(EventType.LOGOUT, subject, ip, true, null);
            return ResponseEntity.noContent().build();
        } catch (RestClientResponseException ex) {
            auditLogService.log(EventType.LOGOUT, subject, ip, false, ex.getMessage());
            throw ex;
        }
    }

    /*
     * GET /auth/introspect — requer JWT
     *
     * Retorna as claims do token corrente sem chamar o Keycloak:
     * o Spring Security já validou a assinatura e populou o Authentication.
     * Útil para que o cliente decodifique as claims sem implementar
     * parsing de JWT localmente.
     */
    @GetMapping("/introspect")
    public ResponseEntity<IntrospectResponse> introspect(@AuthenticationPrincipal Jwt jwt,
                                                          HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String subject = jwt.getSubject();

        // Extrai roles da claim realm_access.roles (mesma lógica do JwtAuthenticationConverter)
        @SuppressWarnings("unchecked")
        Map<String, Object> realmAccess = (Map<String, Object>) jwt.getClaims().get("realm_access");
        List<String> roles = realmAccess != null
                ? (List<String>) realmAccess.get("roles")
                : List.of();

        auditLogService.log(EventType.INTROSPECT, subject, ip, true, null);

        return ResponseEntity.ok(new IntrospectResponse(
                subject,
                jwt.getClaimAsString("preferred_username"),
                roles,
                jwt.getExpiresAt() != null ? jwt.getExpiresAt().getEpochSecond() : 0L
        ));
    }
}
