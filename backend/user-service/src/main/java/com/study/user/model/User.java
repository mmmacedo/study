package com.study.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade JPA que representa um usuário persistido no banco de dados.
 *
 * Decisões de design:
 *
 * @Getter (sem @Setter):
 *   Entidades JPA devem ser tratadas como objetos de domínio, não como bags de dados.
 *   Sem setters públicos, o estado só muda via métodos de negócio explícitos (activate, deactivate).
 *   Isso previne mutação acidental fora da transação.
 *
 * @Builder:
 *   Permite criar instâncias de forma legível:
 *     User.builder().name("João").email("joao@ex.com").build()
 *   Sem exposição de construtor com muitos parâmetros posicionais.
 *
 * @NoArgsConstructor (package-private via Lombok):
 *   O Hibernate exige um construtor sem argumentos para instanciar a entidade
 *   ao carregar do banco. access=PROTECTED evita uso acidental no código de negócio.
 */
@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class User {

    /**
     * UUID gerado pelo banco via gen_random_uuid() (definido na migration).
     * GenerationType.AUTO com UUID deixa o Hibernate delegar a geração ao banco.
     *
     * Por que UUID e não SERIAL/BIGSERIAL?
     *  - Sem colisão ao mesclar dados de múltiplas instâncias ou ambientes
     *  - Não revela volume de registros (contador sequencial é previsível)
     *  - Portabilidade entre bancos de dados
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    /**
     * unique=true cria um índice único no Hibernate, mas o índice real
     * vem da migration (idx_users_email). O Hibernate usa este metadado
     * para gerar DDL quando ddl-auto=create — que nunca usamos em produção.
     * Aqui serve de documentação e para validação de schema (ddl-auto=validate).
     */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * @Enumerated(STRING): armazena o nome do enum ("USER", "ADMIN") no banco.
     * NUNCA use EnumType.ORDINAL — se a ordem do enum mudar, os dados ficam corrompidos.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    /**
     * Soft delete: desativar em vez de deletar preserva histórico e
     * não quebra referências em outras tabelas.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * @CreationTimestamp: Hibernate preenche este campo com o horário atual
     * no momento do INSERT — disponível na memória logo após save(), sem
     * precisar recarregar a entidade do banco.
     * updatable=false garante que o Hibernate nunca sobrescreve numa atualização.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * @UpdateTimestamp: Hibernate atualiza automaticamente no UPDATE.
     * Equivalente ao "updatedAt = now()" sem código manual.
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // =========================================================================
    // Métodos de negócio — a única forma de mutar o estado da entidade
    // =========================================================================

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateProfile(String name) {
        this.name = name;
    }

    public void promoteToAdmin() {
        this.role = Role.ADMIN;
    }

    // =========================================================================
    // Enum de roles
    // =========================================================================

    public enum Role {
        USER,
        ADMIN
    }
}
