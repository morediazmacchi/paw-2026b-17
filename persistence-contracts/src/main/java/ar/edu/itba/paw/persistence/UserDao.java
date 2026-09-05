// Contrato de persistencia para User. Implementacion: UserJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;

import java.util.Optional;

public interface UserDao {
    User create(User user);
    Optional<User> findById(long id);
    Optional<User> findByEmail(String email);
}
