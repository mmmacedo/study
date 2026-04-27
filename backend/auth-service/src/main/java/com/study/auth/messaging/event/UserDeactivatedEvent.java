package com.study.auth.messaging.event;

import java.util.UUID;

public record UserDeactivatedEvent(UUID userId, String email) {}
