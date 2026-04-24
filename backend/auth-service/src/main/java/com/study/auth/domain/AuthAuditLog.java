package com.study.auth.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Tipo do evento: TOKEN, REFRESH, LOGOUT, INTROSPECT
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    // Para TOKEN: username do body da request.
    // Para demais eventos: claim "sub" do JWT (identificador do usuário no Keycloak).
    @Column
    private String username;

    // IP de origem extraído de HttpServletRequest.getRemoteAddr()
    @Column
    private String ipAddress;

    @Column(nullable = false)
    private boolean success;

    // Mensagem de erro quando success = false (ex: "Invalid user credentials")
    @Column
    private String details;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
