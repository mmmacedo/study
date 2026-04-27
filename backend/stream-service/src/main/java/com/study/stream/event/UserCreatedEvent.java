package com.study.stream.event;

import java.util.UUID;

// Record idêntico ao do user-service e auth-service — desserializado do JSON do Kafka.
// O stream-service nunca persiste este evento; apenas o retransmite como SSE.
public record UserCreatedEvent(UUID userId, String name, String email, String role, String temporaryPassword) {}
