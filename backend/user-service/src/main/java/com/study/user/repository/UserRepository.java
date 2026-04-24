package com.study.user.repository;

import com.study.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade User.
 *
 * JpaRepository<User, UUID> fornece gratuitamente:
 *   save(entity)       → INSERT ou UPDATE (detecta pelo ID)
 *   findById(id)       → SELECT por PK
 *   findAll()          → SELECT *
 *   deleteById(id)     → DELETE por PK
 *   existsById(id)     → SELECT COUNT
 *   count()            → SELECT COUNT(*)
 *   ... e mais 15+ métodos
 *
 * Queries derivadas de método:
 *   O Spring Data JPA interpreta o nome do método e gera o SQL automaticamente.
 *   findByEmail   → SELECT * FROM users WHERE email = ?
 *   existsByEmail → SELECT COUNT(*) > 0 FROM users WHERE email = ?
 *
 *   Não há implementação — a interface é suficiente. O Spring gera um proxy
 *   em runtime que implementa esses métodos.
 *
 * Quando usar @Query:
 *   Para queries complexas (JOINs, agregações, subqueries) que não cabem
 *   no nome do método. Neste serviço simples, as queries derivadas são suficientes.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
