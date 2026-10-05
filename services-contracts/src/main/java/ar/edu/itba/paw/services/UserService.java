// Contrato de servicio para User. Implementacion: UserServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.UserRegistrationDTO;
import ar.edu.itba.paw.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    Optional<User> findById(long id);

    // Una sola consulta para varios ids, para armar un mapa id->User sin un
    // findById() por cada uno (ver AdminController.seguimiento()/reportQueueView()).
    List<User> findByIds(List<Long> ids);

    /** Para "Usuarios" del panel de admin (ver AdminController): todos, ordenados por id. */
    List<User> findAll();

    /**
     * Como {@link #findAll()}, pero ya paginado (ver PageResult#of): no hay
     * filtro que justifique una consulta SQL propia con LIMIT/OFFSET, asi que
     * se pagina en memoria sobre el resultado de findAll().
     */
    PageResult<User> findPage(int page, int pageSize);

    /**
     * Para autenticacion: el User que devuelve ya trae password y roles
     * (ver UserDao). Nunca es null "porque no tiene roles": eso es un Set vacio,
     * no ausencia de Optional.
     */
    Optional<User> findByEmail(String email);

    /**
     * Registro propio (login/registro). Hashea rawPassword y asigna ROLE_USER.
     * Si el email ya existe pero es un usuario "fantasma" (fila sin password,
     * que nunca pudo loguearse: puede quedar de datos viejos) reclama esa misma
     * fila en vez de rechazar el registro. Si el email ya tiene password, EMAIL_TAKEN.
     * La cuenta queda sin verificar (ver User.isVerified()) y se le manda un mail
     * con un link de confirmacion: no puede loguearse hasta que lo confirme.
     *
     * data.getRedirectAfterConfirm(): a donde volver despues de confirmar y
     * loguearse (por ejemplo "/eventos/7" si venia de una invitacion). Puede ser
     * null. Viaja dentro del link del mail: se puede abrir en otro navegador o en
     * el celular, donde no hay nada guardado de antes.
     */
    UserRegistrationResult register(UserRegistrationDTO data);

    /**
     * Confirma el mail de una cuenta a partir del token del link que se mandó al
     * registrarse. Optional presente (con el usuario ya confirmado, verified=true)
     * si el token era válido (existía, era de este propósito y no estaba vencido);
     * el token se consume en ese caso. Devuelve el usuario (no solo un boolean)
     * porque quien llama lo usa para iniciarle la sesión ahí mismo, sin pedirle
     * que se loguee de nuevo (ver LoginController.confirmAccount()).
     */
    Optional<User> confirmEmail(String token);

    /**
     * Vuelve a mandar el mail de confirmación (el usuario perdió el anterior o
     * venció). No hace nada si el email no existe, no tiene contraseña (cuenta
     * fantasma) o ya está verificado: siempre "sin efecto visible" para quien
     * llama, así no sirve para averiguar si un email está registrado.
     */
    boolean resendVerificationEmail(String email);

    /**
     * Pide un link para restablecer la contraseña ("olvidé mi contraseña"). No
     * hace nada si el email no existe o no tiene contraseña todavía: mismo
     * motivo que resendVerificationEmail, no hay forma de distinguir los casos
     * desde afuera.
     */
    boolean sendPasswordResetEmail(String email);

    /**
     * Para la pantalla de "elegir nueva contraseña": si el token del link
     * todavía es válido, antes de mostrarle el formulario.
     */
    boolean isResetTokenValid(String token);

    /**
     * Cambia la contraseña usando un token de "olvidé mi contraseña" (a
     * diferencia de changePassword(), acá no hace falta la contraseña actual:
     * el token ya demuestra que el mail es de quien dice ser). Consume el token.
     */
    PasswordResetResult resetPassword(String token, String newPassword);

    /**
     * Actualiza el nombre desde "Mi perfil". El email no se puede cambiar desde
     * aca (ver ProfileController), asi que no hace falta recibirlo ni chequear
     * duplicados.
     */
    boolean updateProfile(long userId, String name);

    /**
     * Cambia la contraseña de un usuario ya logueado. Devuelve false (y no
     * cambia nada) si currentPassword no coincide con la que tiene guardada:
     * a diferencia de register()/el flujo de login, aca no hay duda de que la
     * cuenta existe, asi que hay que probar que sos vos antes de reemplazarla.
     */
    boolean changePassword(long userId, String currentPassword, String newPassword);

    /**
     * Guarda el idioma elegido a mano (boton de la navbar, ver LocaleController)
     * para esta cuenta: de ahi en mas sobrevive a un logout/cookie borrada (se
     * reaplica al loguearse, ver WebAuthConfig.loginSuccessHandler()) y es el
     * idioma en el que se le mandan los mails (ver NotificationServiceImpl).
     * No valida el valor: quien llama ya lo valido contra los idiomas soportados.
     */
    boolean updateLocale(long userId, String locale);

    /**
     * Como {@link #updateLocale(long, String)}, pero para quien solo tiene el email
     * (el {@code Principal} autenticado, ver LocaleController): busca el id el mismo,
     * para que el controller no tenga que resolverlo con un findByEmail() aparte antes
     * de llamar a esto. Si no existe, no hace nada (no deberia pasar para alguien ya
     * logueado, pero no corresponde que esto rompa el cambio de idioma de la pagina).
     */
    boolean updateLocale(String email, String locale);

    /**
     * Suspende la cuenta (ver UserDetailsServiceImpl): no va a poder loguearse
     * hasta que un admin la reactive. Le avisa por mail y por la bandeja.
     */
    boolean suspend(long userId);

    /**
     * Como {@link #suspend(long)}, pero valida primero que {@code caller} pueda
     * suspender a {@code targetId} (ver AdminController.suspender()): nadie se
     * suspende a si mismo, y un admin comun no puede suspender a otro admin ni
     * al superadmin (a diferencia de promover/revocar, esto depende de QUIEN es
     * el objetivo puntual, no solo de que rol tiene quien llama -- eso no lo
     * puede expresar un antMatcher de WebAuthConfig).
     *
     * @throws InvalidAdminActionException si la suspension no esta permitida.
     */
    boolean suspendByAdmin(long targetId, User caller);

    /** Levanta una suspension. Le avisa por mail y por la bandeja. */
    boolean reactivate(long userId);

    /**
     * Le da ROLE_ADMIN a un usuario. Quien puede invocar esto (solo un
     * superadmin) lo controla la seguridad HTTP (ver WebAuthConfig), no este
     * metodo: es una simple asignacion de rol, ya autorizada al llegar aca.
     */
    boolean grantAdmin(long userId);

    /**
     * Le saca ROLE_ADMIN a un usuario (no toca ROLE_SUPERADMIN). Quien puede
     * invocar esto lo controla la seguridad HTTP, igual que grantAdmin() --
     * salvo una cosa que un antMatcher no puede expresar: un superadmin no se
     * puede sacar ROLE_ADMIN a si mismo (ver AdminController.revocar()), porque
     * lo dejaria afuera de /admin/** sin ninguna forma de deshacerlo el mismo.
     *
     * @throws InvalidAdminActionException si targetId es el propio callerId.
     */
    boolean revokeAdmin(long targetId, long callerId);
}
