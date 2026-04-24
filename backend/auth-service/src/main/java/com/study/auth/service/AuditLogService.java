package com.study.auth.service;

import com.study.auth.domain.AuthAuditLog;
import com.study.auth.domain.EventType;
import com.study.auth.repository.AuthAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuthAuditLogRepository repository;

    // @Transactional próprio: garante persistência do log mesmo quando chamado
    // de um contexto sem transação ativa (ex: bloco catch do controller).
    @Transactional
    public void log(EventType eventType, String username, String ipAddress,
                    boolean success, String details) {
        var entry = AuthAuditLog.builder()
                .eventType(eventType)
                .username(username)
                .ipAddress(ipAddress)
                .success(success)
                .details(details)
                .build();
        repository.save(entry);
    }
}
