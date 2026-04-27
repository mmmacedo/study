package com.study.auth.messaging.event;

import java.util.UUID;

public record UserCreatedEvent(
        UUID userId,
        String name,
        String email,
        String role,
        String temporaryPassword
) {}
