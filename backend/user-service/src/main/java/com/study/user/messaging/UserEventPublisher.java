package com.study.user.messaging;

import com.study.user.event.UserCreatedEvent;
import com.study.user.event.UserDeactivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

/**
 * Publica eventos de ciclo de vida do usuário no broker via StreamBridge.
 *
 * StreamBridge é a API imperativa do Spring Cloud Stream para publicação
 * fora do ciclo request/response — ideal para publicar após uma operação de banco.
 *
 * Os nomes dos binding ("user-created-out-0", "user-deactivated-out-0") seguem
 * a convenção: <functionName>-<in|out>-<index>. Declarados em application.yml.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final StreamBridge streamBridge;

    public void publishCreated(UserCreatedEvent event) {
        boolean sent = streamBridge.send("user-created-out-0", event);
        if (sent) {
            log.info("Evento UserCreated publicado userId={}", event.userId());
        } else {
            log.warn("Falha ao publicar UserCreated userId={}", event.userId());
        }
    }

    public void publishDeactivated(UserDeactivatedEvent event) {
        boolean sent = streamBridge.send("user-deactivated-out-0", event);
        if (sent) {
            log.info("Evento UserDeactivated publicado userId={}", event.userId());
        } else {
            log.warn("Falha ao publicar UserDeactivated userId={}", event.userId());
        }
    }
}
