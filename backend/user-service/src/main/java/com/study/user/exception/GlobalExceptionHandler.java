package com.study.user.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tratamento centralizado de exceções para toda a API.
 *
 * @RestControllerAdvice: intercepta exceções lançadas em qualquer @RestController
 * e retorna uma resposta HTTP padronizada em vez de um stack trace ou erro genérico.
 *
 * ProblemDetail (RFC 7807 / RFC 9457):
 *   Padrão HTTP para respostas de erro estruturadas. Spring 6+ tem suporte nativo.
 *   Formato JSON:
 *   {
 *     "type":     "https://example.com/errors/not-found",
 *     "title":    "Not Found",
 *     "status":   404,
 *     "detail":   "Usuário não encontrado: 123e4567-...",
 *     "instance": "/api/users/123e4567-..."
 *   }
 *
 * Por que centralizar aqui em vez de tratar em cada controller?
 *   - Consistência: todas as respostas de erro têm o mesmo formato
 *   - DRY: sem duplicação de try/catch em cada endpoint
 *   - Separação de responsabilidades: o controller só lida com o happy path
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        log.warn("Usuário não encontrado: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("/errors/user-not-found"));
        problem.setTitle("Usuário não encontrado");
        return problem;
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        log.warn("Email duplicado: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("/errors/duplicate-email"));
        problem.setTitle("Email já cadastrado");
        return problem;
    }

    /**
     * Erros de validação Bean Validation (@Valid no controller).
     * Retorna 400 com a lista de campos inválidos.
     *
     * Exemplo de resposta:
     * {
     *   "status": 400,
     *   "title": "Dados inválidos",
     *   "detail": "Um ou mais campos falharam na validação",
     *   "errors": { "email": "Email inválido", "name": "Nome é obrigatório" }
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "inválido",
                        (existing, duplicate) -> existing  // mantém o primeiro erro por campo
                ));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos falharam na validação");
        problem.setType(URI.create("/errors/validation"));
        problem.setTitle("Dados inválidos");
        problem.setProperty("errors", errors);
        return problem;
    }
}
