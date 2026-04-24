package com.study.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /*
     * RestClient com baseUrl apontando para o Keycloak.
     *
     * RestClient (Spring Boot 3.2+) é a API síncrona moderna do Spring —
     * substitui o RestTemplate mantendo a mesma semântica mas com fluent API.
     * Para WebFlux usaríamos WebClient; aqui Spring MVC → RestClient.
     *
     * baseUrl: carregado de ${keycloak.base-url} no application.yml.
     *   Local:  http://localhost:8180
     *   Docker: http://keycloak:8180 (via KEYCLOAK_URL env var)
     *
     * O RestClient.Builder é injetado pelo Spring Boot com as configurações
     * padrão (message converters, timeout, etc.).
     */
    @Bean
    public RestClient keycloakRestClient(RestClient.Builder builder,
                                          @Value("${keycloak.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
