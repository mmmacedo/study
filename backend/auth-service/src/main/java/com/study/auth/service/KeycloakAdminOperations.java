package com.study.auth.service;

import java.util.UUID;

public interface KeycloakAdminOperations {
    void createUser(UUID userId, String username, String email, String role, String temporaryPassword);
    void disableUser(UUID userId);
}
