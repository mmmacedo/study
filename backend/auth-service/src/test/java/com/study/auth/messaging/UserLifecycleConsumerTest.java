package com.study.auth.messaging;

import com.study.auth.messaging.event.UserCreatedEvent;
import com.study.auth.messaging.event.UserDeactivatedEvent;
import com.study.auth.service.KeycloakAdminOperations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Teste unitário dos consumers de mensageria.
 *
 * Não precisa de Spring — testa apenas que o Consumer delega corretamente ao service.
 * UserLifecycleConsumer é um @Configuration com @Bean methods;
 * para teste unitário basta chamar o método e executar o Consumer retornado.
 */
@ExtendWith(MockitoExtension.class)
class UserLifecycleConsumerTest {

    @Mock
    KeycloakAdminOperations keycloakAdminService;

    @InjectMocks
    UserLifecycleConsumer consumer;

    @Test
    @DisplayName("userCreated consumer → delega ao KeycloakAdminService.createUser")
    void userCreated_delegatesToAdminService() {
        var event = new UserCreatedEvent(
                UUID.randomUUID(), "user@test.com", "user@test.com", "USER", "Temp1234!"
        );

        consumer.userCreated().accept(event);

        verify(keycloakAdminService).createUser(
                event.userId(),
                event.email(),
                event.email(),
                event.role(),
                event.temporaryPassword()
        );
    }

    @Test
    @DisplayName("userDeactivated consumer → delega ao KeycloakAdminService.disableUser")
    void userDeactivated_delegatesToAdminService() {
        var event = new UserDeactivatedEvent(UUID.randomUUID(), "user@test.com");

        consumer.userDeactivated().accept(event);

        verify(keycloakAdminService).disableUser(event.userId());
    }
}
