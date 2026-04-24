package com.study.user.dto;

import com.study.user.model.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de saída — representa o usuário na resposta HTTP.
 *
 * Por que não retornar a entidade User diretamente?
 *
 *   1. DESACOPLAMENTO: a entidade pode ter campos internos (senha, flags de auditoria)
 *      que não devem ser expostos na API. O DTO é o contrato público.
 *
 *   2. EVOLUÇÃO INDEPENDENTE: o schema do banco pode mudar sem quebrar a API,
 *      e a API pode mudar sem alterar o banco.
 *
 *   3. SERIALIZAÇÃO SEGURA: entidades JPA com relacionamentos Lazy podem
 *      causar LazyInitializationException durante a serialização JSON
 *      se a sessão JPA já estiver fechada (com open-in-view: false).
 *      DTOs são POJOs simples — sem esse problema.
 *
 * Record como DTO de RESPOSTA (saída): ideal pelo mesmo motivo que o request —
 * imutável, conciso, geração automática de equals/hashCode/toString.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        User.Role role,
        boolean active,
        OffsetDateTime createdAt
) {}
