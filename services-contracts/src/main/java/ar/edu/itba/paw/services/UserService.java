// Contrato de servicio para User. Implementacion: UserServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;

import java.util.Optional;

public interface UserService {
    Optional<User> findById(long id);

    /**
     * Busca un user por email; si no existe, lo crea. Nunca pisa un user existente.
     */
    User findOrCreate(String email, String name);
}
