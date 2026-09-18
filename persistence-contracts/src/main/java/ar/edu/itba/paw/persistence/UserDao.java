// Contrato de persistencia para User. Implementacion: UserJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;

import java.util.Optional;
import java.util.Set;

public interface UserDao {
    User create(User user);

    // Optional<User> siempre trae los roles ya cargados (ver User.getRoles()):
    // quien pide un usuario por id/email lo necesita para autenticacion, no aparte.
    Optional<User> findById(long id);
    Optional<User> findByEmail(String email);

    Set<String> findRoles(long userId);
    void addRole(long userId, String role);

    void setPassword(long userId, String passwordHash);

    void updateProfile(long userId, String name, String email);

    void setVerified(long userId, boolean verified);
}
