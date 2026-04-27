package com.study.auth.messaging;

import com.study.auth.messaging.event.UserCreatedEvent;
import com.study.auth.messaging.event.UserDeactivatedEvent;
import com.study.auth.service.KeycloakAdminOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Consumers de eventos de ciclo de vida do usuário.
 *
 * Spring Cloud Stream detecta beans do tipo Consumer<T> pelo nome declarado
 * em spring.cloud.function.definition e cria os bindings automaticamente.
 *
 * Cada Consumer é um listener independente — falha em um não afeta o outro.
 * O binder Kafka trata o retry e o DLQ conforme configurado em application.yml.
 *
 * @Configuration + @Bean em vez de @Component + implementação de interface:
 *   A função Java pura (Consumer<>) é mais limpa que implementar uma interface.
 *   Testável sem Spring — apenas instanciar a classe e chamar o método.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UserLifecycleConsumer {

    private final KeycloakAdminOperations keycloakAdminService;

    @Bean
    public Consumer<UserCreatedEvent> userCreated() {
        return event -> {
            log.info("Recebido UserCreatedEvent userId={}", event.userId());
            keycloakAdminService.createUser(
                    event.userId(),
                    event.email(),
                    event.email(),
                    event.role(),
                    event.temporaryPassword()
            );
        };
    }

    @Bean
    public Consumer<UserDeactivatedEvent> userDeactivated() {
        return event -> {
            log.info("Recebido UserDeactivatedEvent userId={}", event.userId());
            keycloakAdminService.disableUser(event.userId());
        };
    }
}
