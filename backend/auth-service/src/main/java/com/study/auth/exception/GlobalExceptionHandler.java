package com.study.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * Keycloak retorna HTTP 400 quando as credenciais são inválidas,
     * o refresh_token expirou, etc. Convertemos para 401 + ProblemDetail
     * — o cliente não precisa saber que a chamada foi delegada ao Keycloak.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail handleKeycloakError(RestClientResponseException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Authentication failed");
        problem.setDetail("Invalid credentials or token");
        return problem;
    }

    /*
     * Campos obrigatórios ausentes ou inválidos no body da requisição.
     * Retorna 422 com mapa de erros por campo.
     *
     * Merge function (_, existing -> existing): evita IllegalStateException quando
     * o mesmo campo tem duas validações falhando simultaneamente
     * (ex: @NotBlank + @Size com valor vazio geram dois FieldError para o mesmo campo).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (existing, duplicate) -> existing
                ));

        var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
