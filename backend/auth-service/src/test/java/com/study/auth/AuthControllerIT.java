package com.study.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Testes de integração do AuthController.
 *
 * O que estes testes verificam:
 *   - Configuração de segurança (endpoints públicos vs protegidos)
 *   - Flyway: o banco sobe sem erros de migration
 *   - Bean Validation: body inválido → 422
 *
 * O que estes testes NÃO verificam:
 *   - Fluxo completo token/refresh/logout (requer Keycloak real)
 *   - Conteúdo do TokenResponse (dependente do Keycloak)
 *
 * @ServiceConnection elimina a necessidade de @DynamicPropertySource:
 *   O Spring Boot configura automaticamente spring.datasource.* a partir
 *   do container PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void tokenEndpointIsPublicAndRejectsEmptyBody() throws Exception {
        // Público (não exige JWT) mas rejeita body inválido com 422 (Bean Validation).
        // Distingue de 401 — confirma que a segurança não bloqueou a requisição.
        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refreshEndpointIsPublicAndRejectsEmptyBody() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void introspectRequiresJwt() throws Exception {
        // Sem Authorization header → Spring Security retorna 401
        mockMvc.perform(get("/auth/introspect"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresJwt() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
