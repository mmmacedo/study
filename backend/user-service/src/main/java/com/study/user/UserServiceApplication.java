package com.study.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do user-service.
 *
 * @SpringBootApplication é atalho para três anotações:
 *   @Configuration        → esta classe pode declarar @Bean
 *   @EnableAutoConfiguration → Spring Boot configura automaticamente os componentes
 *                              encontrados no classpath (JPA, Web, Security, etc.)
 *   @ComponentScan        → escaneia o pacote atual e subpacotes em busca de
 *                           @Service, @Repository, @Controller, @Component
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
