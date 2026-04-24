package com.study.user.controller;

import com.study.user.dto.CreateUserRequest;
import com.study.user.dto.UserResponse;
import com.study.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST do user-service.
 *
 * Responsabilidades desta camada:
 *   - Receber e deserializar requests HTTP
 *   - Validar entrada (@Valid)
 *   - Delegar ao Service (sem lógica de negócio aqui)
 *   - Construir a resposta HTTP com status code correto
 *
 * @RestController = @Controller + @ResponseBody:
 *   Cada método retorna o objeto direto serializado como JSON,
 *   sem precisar de @ResponseBody em cada método.
 *
 * @RequestMapping("/api/users"):
 *   Prefixo comum a todos os endpoints. O api-gateway roteia /api/users/**
 *   para este serviço (configurado no application.yml do gateway).
 *
 * Autorização com @PreAuthorize:
 *   Valida as claims do JWT recebido. O token já foi validado pelo api-gateway —
 *   aqui apenas checamos as roles para controle de acesso granular.
 *   hasRole('ADMIN') verifica a claim "roles" no JWT emitido pelo Keycloak.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Lista todos os usuários.
     * Acesso restrito a ADMIN — usuários comuns não veem a lista completa.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    /**
     * Busca um usuário pelo ID.
     * Um usuário pode ver seu próprio perfil; ADMIN vê qualquer um.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    public ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * Cria um novo usuário.
     *
     * HTTP 201 Created com Location header:
     *   A convenção REST para criação bem-sucedida é 201, não 200.
     *   O header Location aponta para o recurso criado, permitindo que o
     *   cliente navegue para /api/users/{id} sem outra requisição.
     *
     * @Valid: aciona as validações Bean Validation do CreateUserRequest.
     *   Falhas são capturadas pelo GlobalExceptionHandler → 400.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /**
     * Desativa um usuário (soft delete).
     *
     * HTTP 204 No Content: operação bem-sucedida sem corpo de resposta.
     * Convencional para operações de deleção/desativação.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
