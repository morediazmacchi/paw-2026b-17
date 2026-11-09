// Contrato de servicio para User. Implementacion: UserServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.UserRegistrationDTO;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.UserAccess;

import java.util.List;
import java.util.Optional;

public interface UserService {

    // Largo minimo de una contraseña. Los forms ya lo validan para dar feedback; los metodos que guardan una
    // contraseña (register, resetPassword, changePassword) lo vuelven a chequear: la regla es del service.
    int MIN_PASSWORD_LENGTH = 8;

    // Largo maximo del nombre (updateProfile() devuelve INVALID_NAME si esta vacio o lo supera).
    int MAX_NAME_LENGTH = 150;

    // Largo maximo de la biografia del perfil publico (updateBio() devuelve INVALID si lo supera).
    int MAX_BIO_LENGTH = 500;

    Optional<User> findById(long id);

    /**
     * Id y roles del usuario para decidir un permiso sobre el, sin cargar el User (ni su contrasena).
     * Lo usan las reglas de AdminSecurity.
     */
    Optional<UserAccess> findAccessById(long id);

    // Una sola consulta para varios ids, para armar un mapa id->User sin un
    // findById() por cada uno (ver AdminController.seguimiento()/reportQueueView()).
    List<User> findByIds(List<Long> ids);

    /** Para "Usuarios" del panel de admin (ver AdminController): todos, ordenados por id. */
    List<User> findAll();

    /**
     * Como {@link #findAll()}, pero ya paginado en SQL: la pagina pedida se
     * ajusta a la ultima con usuarios (igual que la busqueda de eventos).
     */
    PageResult<User> findPage(int page);

    /**
     * Para autenticacion: el User que devuelve ya trae password y roles
     * (ver UserDao). Nunca es null "porque no tiene roles": eso es un Set vacio,
     * no ausencia de Optional.
     */
    Optional<User> findByEmail(String email);

    /**
     * Registro propio (login/registro). Hashea rawPassword y asigna el rol USER.
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
    UpdateProfileResult updateProfile(long userId, String name);

    /**
     * Guarda la biografia del perfil publico de la persona. Se recorta; una biografia vacia la borra (queda sin
     * biografia). INVALID si supera {@link #MAX_BIO_LENGTH}: el form ya lo valida para dar feedback.
     */
    UpdateBioResult updateBio(long userId, String bio);

    /**
     * Cambia la foto de portada del perfil publico y borra la anterior. Recibe los bytes crudos (no un MultipartFile,
     * que es de la capa web); la valida {@link ImageService#create}.
     *
     * @throws ar.edu.itba.paw.model.exception.InvalidImageException si el archivo esta vacio, pesa demasiado o no es una
     *         imagen JPEG, PNG, GIF o WebP: en ese caso no se escribe nada
     */
    UpdateCoverResult updateCover(long userId, byte[] data, String contentType, String fileName);

    /** Quita la foto de portada (el perfil vuelve a la portada por defecto) y borra la imagen. */
    UpdateCoverResult removeCover(long userId);

    /**
     * Cambia la contraseña de un usuario ya logueado. Devuelve WRONG_CURRENT_PASSWORD (y no
     * cambia nada) si currentPassword no coincide con la que tiene guardada:
     * a diferencia de register()/el flujo de login, aca no hay duda de que la
     * cuenta existe, asi que hay que probar que sos vos antes de reemplazarla. Devuelve WEAK_PASSWORD
     * (tampoco cambia nada) si la nueva es mas corta que {@link #MIN_PASSWORD_LENGTH}.
     */
    ChangePasswordResult changePassword(long userId, String currentPassword, String newPassword);

    /**
     * Guarda el idioma elegido a mano (boton de la navbar, ver LocaleController)
     * para esta cuenta: de ahi en mas sobrevive a un logout/cookie borrada (se
     * reaplica al loguearse, ver WebAuthConfig.loginSuccessHandler()) y es el
     * idioma en el que se le mandan los mails (ver NotificationServiceImpl).
     * Solo guarda los idiomas soportados (ver SupportedLocale): con otro valor no escribe nada y devuelve false.
     */
    boolean updateLocale(long userId, String locale);

    /**
     * Suspension desde el panel de admin: igual que {@link #suspend(long)}, pero valida como regla de
     * negocio que nadie se suspenda a si mismo (OWN_ACCOUNT, antes de escribir; igual que revokeAdmin()).
     * Quien puede suspender a quien segun el rol (a otro admin solo un superadmin) NO se valida aca: lo
     * decide @PreAuthorize en AdminController (ver AdminSecurity.canSuspend).
     */
    SuspendResult suspend(User caller, long targetId);

    /**
     * Suspende la cuenta (ver UserDetailsServiceImpl): no va a poder loguearse
     * hasta que un admin la reactive. Le avisa por mail y por la bandeja, y cierra sus sesiones abiertas.
     * Sin ninguna validacion de quien la pide: es para los caminos ya autorizados (la baja por moderacion,
     * que ya paso por AdminSecurity.canResolveReport). El panel de admin usa
     * {@link #suspend(User, long)}.
     */
    boolean suspend(long userId);

    /** Levanta una suspension. Le avisa por mail y por la bandeja. */
    boolean reactivate(long userId);

    /**
     * Le da el rol ADMIN a un usuario. Quien puede invocar esto (solo un
     * superadmin) lo controla la seguridad HTTP (ver WebAuthConfig), no este
     * metodo: es una simple asignacion de rol, ya autorizada al llegar aca.
     */
    boolean grantAdmin(long userId);

    /**
     * Le saca el rol ADMIN a un usuario (no toca SUPERADMIN). Quien puede invocar esto (solo un
     * superadmin) lo controla la seguridad HTTP (ver WebAuthConfig), no este metodo. Si valida, como
     * reglas de negocio, que nadie se lo saque a si mismo (OWN_ACCOUNT) y que el objetivo no sea un
     * superadmin (TARGET_SUPERADMIN: no hay herencia de roles, sin ADMIN perderia el acceso a /admin/**).
     */
    RevokeAdminResult revokeAdmin(User caller, long targetId);
}
