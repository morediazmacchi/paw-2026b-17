package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;

/**
 * Los mails de la cuenta propia: confirmar el mail, cuenta ya registrada, restablecer la clave, cuenta suspendida / reactivada. Reciben
 * la cuenta ya cargada y no consultan nada (por eso viven aparte de NotificationService, que si necesita buscar
 * cuentas: UserService depende de este y NotificationService depende de UserService, y juntos serian un ciclo).
 *
 * Como en NotificationService, todos son @Async en la implementacion: devuelven enseguida y el mail sale en otro hilo.
 */
public interface AccountNotificationService {

    /**
     * Alguien se registro (o completo una cuenta fantasma) -> mail con el link para
     * confirmar el mail. redirectAfterConfirm (puede ser null) viaja en el link.
     */
    void notifyAccountVerification(User user, String token, String redirectAfterConfirm);

    /**
     * Alguien intento registrarse con el mail de una cuenta que ya existe -> le avisa a su dueño (con links para
     * iniciar sesion o restablecer la contraseña). A quien intento registrarse no se le dice nada de esto.
     */
    void notifyAccountAlreadyRegistered(User user);

    /** Alguien pidio "olvide mi contraseña" -> mail con el link para elegir una nueva. */
    void notifyPasswordReset(User user, String token);

    /** Un admin suspendio la cuenta -> le avisa a esa persona. */
    void notifyUserSuspended(User user);

    /** Un admin reactivo la cuenta -> le avisa a esa persona. */
    void notifyUserReactivated(User user);
}
