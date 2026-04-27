package com.study.stream.event;

import java.util.UUID;

// Record idêntico ao do user-service — desserializado do JSON do Kafka.
public record UserDeactivatedEvent(UUID userId, String email) {}
