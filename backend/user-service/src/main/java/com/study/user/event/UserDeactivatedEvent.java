package com.study.user.event;

import java.util.UUID;

/**
 * Evento publicado no tópico "user-deactivated" após desativar um usuário.
 *
 * O auth-service consome este evento e desabilita o usuário no Keycloak,
 * impedindo novos logins. Sessions ativas expiram naturalmente pelo TTL do JWT.
 *
 * email incluído para facilitar consultas de auditoria sem join adicional.
 */
public record UserDeactivatedEvent(UUID userId, String email) {}
