package com.study.user.event;

import java.util.UUID;

/**
 * Evento publicado no tópico "user-created" após persistir um novo usuário.
 *
 * O auth-service consome este evento e cria o usuário correspondente no Keycloak
 * usando o mesmo UUID — garantindo consistência de ID entre os dois sistemas.
 *
 * temporaryPassword: gerado pelo user-service e incluído no evento para que o
 * auth-service possa configurar a credencial inicial no Keycloak.
 * O usuário deverá alterá-la no primeiro login.
 */
public record UserCreatedEvent(
        UUID userId,
        String name,
        String email,
        String role,
        String temporaryPassword
) {}
