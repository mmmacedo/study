package com.study.user.service;

import com.study.user.dto.CreateUserRequest;
import com.study.user.dto.UserResponse;
import com.study.user.exception.DuplicateEmailException;
import com.study.user.exception.UserNotFoundException;
import com.study.user.model.User;
import com.study.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Camada de serviço do user-service.
 *
 * Responsabilidades desta camada:
 *   - Lógica de negócio (validação de email duplicado, regras de ativação)
 *   - Orquestração de operações (busca + validação + persistência)
 *   - Mapeamento entre entidades e DTOs
 *   - Gerenciamento de transações via @Transactional
 *
 * O que NÃO pertence aqui:
 *   - Parsing de HTTP (responsabilidade do Controller)
 *   - SQL direto (responsabilidade do Repository)
 *   - Autenticação/autorização (responsabilidade do Spring Security)
 *
 * @RequiredArgsConstructor: Lombok gera construtor com todos os campos final.
 *   Injeção via construtor é preferível a @Autowired em campo:
 *   - Permite teste unitário sem Spring (apenas instanciar a classe)
 *   - Deixa dependências explícitas e imutáveis
 *   - Detecta dependências circulares em tempo de inicialização
 *
 * @Slf4j: Lombok injeta um campo estático `log` do tipo Logger (SLF4J).
 *   Com o ECS structured logging configurado, os logs gerados aqui
 *   automaticamente incluem trace.id e span.id para correlação com o Jaeger.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Lista todos os usuários.
     *
     * @Transactional(readOnly = true):
     *   - Otimiza a sessão JPA para leitura (sem dirty checking)
     *   - No PostgreSQL, permite uso de réplicas de leitura (read replicas)
     *   - Deixa explícita a intenção: este método não modifica dados
     */
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        log.debug("Listando todos os usuários");
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Busca um usuário pelo ID.
     *
     * Optional.orElseThrow: evita null checks e deixa o fluxo explícito.
     * UserNotFoundException é capturada pelo GlobalExceptionHandler → 404.
     */
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        log.debug("Buscando usuário id={}", id);
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Cria um novo usuário.
     *
     * Validação de email duplicado:
     *   Verificar antes de tentar inserir evita capturar DataIntegrityViolationException
     *   do banco, que é mais difícil de tratar e gera mensagem de erro opaca.
     *   A constraint UNIQUE no banco é a garantia final — esta verificação é para
     *   gerar um erro de negócio claro.
     *
     * @Transactional (sem readOnly): abre transação de escrita.
     *   O save() e o existsByEmail() rodam na mesma transação — consistência garantida.
     */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        log.info("Criando usuário email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .build();

        User saved = userRepository.save(user);
        log.info("Usuário criado id={}", saved.getId());
        return toResponse(saved);
    }

    /**
     * Desativa um usuário (soft delete).
     *
     * Não remove o registro do banco — apenas muda o flag active para false.
     * O método de negócio deactivate() na entidade encapsula a mutação.
     */
    @Transactional
    public void deactivate(UUID id) {
        log.info("Desativando usuário id={}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.deactivate();
        // Não precisa chamar save() — o Hibernate detecta a mudança automaticamente
        // ao fim da transação (dirty checking) e gera o UPDATE.
    }

    // =========================================================================
    // Mapeamento privado — separado de um Mapper dedicado para simplicidade
    // =========================================================================

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
