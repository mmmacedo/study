package com.study.user.exception;

import java.util.UUID;

/**
 * Lançada quando um usuário não é encontrado pelo ID.
 * O GlobalExceptionHandler converte em HTTP 404 com ProblemDetail (RFC 7807).
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("Usuário não encontrado: " + id);
    }
}
