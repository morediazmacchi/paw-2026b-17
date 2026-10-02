// Contrato de persistencia para User. Implementacion: UserJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;

import java.util.List;
import java.util.Optional;

public interface UserDao {
    User create(User user);

    // Optional<User> siempre trae los roles ya cargados (ver User.getRoles()):
    // quien pide un usuario por id/email lo necesita para autenticacion, no aparte.
    Optional<User> findById(long id);
    Optional<User> findByEmail(String email);

    // Para "Usuarios" del panel de admin (ver AdminController): todos, con sus
    // roles ya cargados, ordenados por id.
    List<User> findAll();

    // Una sola consulta para varios ids (WHERE id IN (...)), para armar un mapa
    // id->User sin un findById() por cada uno (ver AdminController.seguimiento()/
    // reportQueueView()). Mismos ids repetidos o inexistentes se ignoran en
    // silencio; el orden del resultado no esta garantizado.
    List<User> findByIds(List<Long> ids);

    boolean addRole(long userId, String role);

    // Complemento de addRole(): usado para revocar ROLE_ADMIN (ver
    // UserService.revokeAdmin()). No falla si el usuario no tenia ese rol.
    boolean removeRole(long userId, String role);

    boolean setPassword(long userId, String passwordHash);

    boolean updateProfile(long userId, String name);

    boolean setVerified(long userId, boolean verified);

    boolean setLocale(long userId, String locale);

    // Suspension por un admin (ver UserService.suspend()/reactivate()):
    // UserDetailsServiceImpl la lee para bloquear el login mientras este en true.
    boolean setSuspended(long userId, boolean suspended);
}
