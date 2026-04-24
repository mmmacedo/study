package com.study.user.exception;

/**
 * Lançada quando já existe um usuário com o email informado.
 * O GlobalExceptionHandler converte em HTTP 409 Conflict com ProblemDetail (RFC 7807).
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("Já existe um usuário com o email: " + email);
    }
}
