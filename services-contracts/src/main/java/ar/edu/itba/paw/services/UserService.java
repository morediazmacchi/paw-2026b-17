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

    /**
     * Para autenticacion: el User que devuelve ya trae password y roles
     * (ver UserDao). Nunca es null "porque no tiene roles": eso es un Set vacio,
     * no ausencia de Optional.
     */
    Optional<User> findByEmail(String email);

    /**
     * Registro propio (login/registro), no findOrCreate. Hashea rawPassword y
     * asigna ROLE_USER. Si el email ya existe pero es un usuario "fantasma"
     * (findOrCreate, sin password: nunca pudo loguearse) reclama esa misma fila
     * en vez de rechazar el registro. Si el email ya tiene password, EMAIL_TAKEN.
     */
    UserRegistrationResult register(String email, String name, String rawPassword);

    /**
     * Actualiza nombre y email desde "Mi perfil". Si el nuevo email ya es de
     * otro usuario, no toca nada y devuelve EMAIL_TAKEN.
     */
    ProfileUpdateResult updateProfile(long userId, String name, String email);

    /**
     * Cambia la contraseña de un usuario ya logueado. Devuelve false (y no
     * cambia nada) si currentPassword no coincide con la que tiene guardada:
     * a diferencia de register()/el flujo de login, aca no hay duda de que la
     * cuenta existe, asi que hay que probar que sos vos antes de reemplazarla.
     */
    boolean changePassword(long userId, String currentPassword, String newPassword);
}
