package com.study.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para criação de usuário.
 *
 * Por que Record em vez de classe com Lombok?
 *   Records Java (desde Java 16, estável no 25) são imutáveis por definição:
 *   - Construtor canônico gerado automaticamente
 *   - Sem setters — dados de entrada não devem ser mutados após recebidos
 *   - equals/hashCode/toString gerados automaticamente
 *   - Sintaxe mais concisa que @Data + @NoArgsConstructor
 *
 *   Para DTOs de REQUEST (entrada), record é ideal.
 *   Para entidades JPA, record NÃO funciona (Hibernate precisa de construtor vazio e setters).
 *
 * Validações Jakarta Bean Validation:
 *   @NotBlank: não nulo E não vazio E não só espaços
 *   @Email: formato de email válido (regex básico)
 *   @Size: comprimento mínimo/máximo da string
 *
 *   Essas anotações são verificadas quando o controller recebe a requisição
 *   e o parâmetro está anotado com @Valid. Falhas geram 400 Bad Request.
 */
public record CreateUserRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
        String name,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {}
