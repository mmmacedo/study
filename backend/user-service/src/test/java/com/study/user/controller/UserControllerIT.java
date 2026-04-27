package com.study.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.user.dto.CreateUserRequest;
import com.study.user.event.UserCreatedEvent;
import com.study.user.event.UserDeactivatedEvent;
import com.study.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Teste de integração do UserController.
 *
 * @Import(TestChannelBinderConfiguration.class):
 *   Substitui o Kafka binder por um binder em memória.
 *   OutputDestination.receive("user-created") lê mensagens publicadas via StreamBridge
 *   sem precisar de Kafka real — os testes ficam mais rápidos e sem dependência externa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestChannelBinderConfiguration.class)
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

    @Autowired
    OutputDestination outputDestination;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        // Drena mensagens residuais de testes anteriores para isolar cada teste
        outputDestination.clear();
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
    @DisplayName("POST /api/users → publica UserCreatedEvent no tópico user-created")
    @WithMockUser(roles = "ADMIN")
    void create_publishesUserCreatedEvent() throws Exception {
        var request = new CreateUserRequest("João Silva", "joao@example.com");

        String body = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String userId = objectMapper.readTree(body).get("id").asText();

        Message<byte[]> msg = outputDestination.receive(1000, "user-created");
        assertThat(msg).isNotNull();

        UserCreatedEvent event = objectMapper.readValue(msg.getPayload(), UserCreatedEvent.class);
        assertThat(event.userId()).hasToString(userId);
        assertThat(event.email()).isEqualTo("joao@example.com");
        assertThat(event.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("DELETE /api/users/{id} → publica UserDeactivatedEvent no tópico user-deactivated")
    @WithMockUser(roles = "ADMIN")
    void deactivate_publishesUserDeactivatedEvent() throws Exception {
        var request = new CreateUserRequest("Maria", "maria@example.com");

        String createBody = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String userId = objectMapper.readTree(createBody).get("id").asText();

        // Drena o UserCreatedEvent para isolar o assert do delete
        outputDestination.receive(1000, "user-created");

        mockMvc.perform(delete("/api/users/" + userId))
                .andExpect(status().isNoContent());

        Message<byte[]> msg = outputDestination.receive(1000, "user-deactivated");
        assertThat(msg).isNotNull();

        UserDeactivatedEvent event = objectMapper.readValue(msg.getPayload(), UserDeactivatedEvent.class);
        assertThat(event.userId()).hasToString(userId);
        assertThat(event.email()).isEqualTo("maria@example.com");
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
