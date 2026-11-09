// Contrato de persistencia para User. Implementacion: UserJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.UserAccess;

import java.util.List;
import java.util.Optional;

public interface UserDao {
    User create(String email, String name);

    // Optional<User> siempre trae los roles ya cargados (ver User.getRoles()):
    // quien pide un usuario por id/email lo necesita para autenticacion, no aparte.
    Optional<User> findById(long id);

    // Lectura acotada para autorizar (ver AdminSecurity): id y roles, sin cargar el User (ni su
    // contrasena). Vacio si el usuario no existe.
    Optional<UserAccess> findAccessById(long id);
    Optional<User> findByEmail(String email);

    // Para "Usuarios" del panel de admin (ver AdminController): una pagina de usuarios
    // ordenados por id, con sus roles ya cargados. Pagina sobre la tabla users (no sobre
    // las filas del JOIN con roles: un usuario con dos roles son dos filas). countAll()
    // es el total para calcular las paginas.
    List<User> findPage(int limit, int offset);

    long countAll();

    // Una sola consulta para varios ids (WHERE id IN (...)), para armar un mapa
    // id->User sin un findById() por cada uno (ver AdminController.seguimiento()/
    // reportQueueView()). Mismos ids repetidos o inexistentes se ignoran en
    // silencio; el orden del resultado no esta garantizado.
    List<User> findByIds(List<Long> ids);

    // Biografia del perfil publico (null = sin biografia). false si no existe la cuenta.
    boolean updateBio(long userId, String bio);

    // Foto de portada del perfil publico (null = portada por defecto). false si no existe la cuenta.
    boolean updateCoverImage(long userId, Long imageId);

    boolean addRole(long userId, Role role);

    // Complemento de addRole(): usado para revocar ADMIN (ver
    // UserService.revokeAdmin()). No falla si el usuario no tenia ese rol.
    boolean removeRole(long userId, Role role);

    boolean setPassword(long userId, String passwordHash);

    boolean updateProfile(long userId, String name);

    boolean setVerified(long userId, boolean verified);

    boolean setLocale(long userId, String locale);

    // Suspension por un admin (ver UserService.suspend()/reactivate()):
    // UserDetailsServiceImpl la lee para bloquear el login mientras este en true.
    boolean setSuspended(long userId, boolean suspended);
}
