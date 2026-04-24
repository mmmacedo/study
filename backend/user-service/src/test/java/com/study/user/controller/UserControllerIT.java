package com.study.user.controller;

import com.study.user.dto.CreateUserRequest;
import com.study.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Teste de integração do UserController.
 *
 * @SpringBootTest: sobe o contexto completo do Spring Boot — todos os beans,
 *   configurações, filtros de segurança, conversor JSON. Testa o comportamento
 *   real da aplicação, não mocks isolados.
 *
 * @AutoConfigureMockMvc: configura o MockMvc sem iniciar um servidor HTTP real.
 *   MockMvc chama os controllers diretamente na JVM — mais rápido que servidor real,
 *   mas cobre toda a camada web (serialização, validação, segurança, handlers).
 *
 * @Testcontainers + @Container:
 *   Sobe um PostgreSQL Docker real antes dos testes e derruba ao final.
 *   Por que não H2 em memória?
 *     - H2 tem dialeto SQL diferente do PostgreSQL (tipos, funções, constraints)
 *     - Testa exatamente o que vai rodar em produção
 *     - Flyway roda as migrations reais no container
 *
 * @ServiceConnection:
 *   Spring Boot 3.1+: detecta automaticamente que é um PostgreSQLContainer
 *   e configura spring.datasource.url/username/password sem properties manuais.
 *   Elimina o padrão antigo de @DynamicPropertySource.
 *
 * @WithMockUser:
 *   Injeta um usuário autenticado no SecurityContext sem rodar o fluxo OAuth2.
 *   roles={"ADMIN"} simula um token com role ADMIN para endpoints protegidos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/users → 201 com Location header")
    @WithMockUser(roles = "ADMIN")
    void create_returnsCreated() throws Exception {
        var request = new CreateUserRequest("João Silva", "joao@example.com");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /api/users com email duplicado → 409")
    @WithMockUser(roles = "ADMIN")
    void create_duplicateEmail_returnsConflict() throws Exception {
        var request = new CreateUserRequest("João", "joao@example.com");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email já cadastrado"));
    }

    @Test
    @DisplayName("POST /api/users com dados inválidos → 400 com detalhes de campo")
    @WithMockUser(roles = "ADMIN")
    void create_invalidData_returnsBadRequest() throws Exception {
        var request = new CreateUserRequest("", "nao-e-um-email");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/users/{id} com ID inexistente → 404")
    @WithMockUser(roles = "ADMIN")
    void findById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/users/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Usuário não encontrado"));
    }

    @Test
    @DisplayName("GET /api/users sem autenticação → 401")
    void findAll_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/users/{id} → 204 e usuário desativado")
    @WithMockUser(roles = "ADMIN")
    void deactivate_returnsNoContent() throws Exception {
        var request = new CreateUserRequest("Maria", "maria@example.com");

        String response = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(delete("/api/users/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
