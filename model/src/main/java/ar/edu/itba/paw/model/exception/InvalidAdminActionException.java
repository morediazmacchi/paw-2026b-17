package ar.edu.itba.paw.model.exception;

/**
 * Un admin intento una accion sobre una cuenta que no deberia (ver
 * UserServiceImpl.suspendByAdmin()/revokeAdmin()): sobre si mismo, o (sin ser
 * superadmin) sobre otro admin/el superadmin. Unchecked: la UI ya esconde el
 * boton en todos los casos, asi que solo se llega aca con un POST armado a mano.
 */
public class InvalidAdminActionException extends RuntimeException {

    public enum Reason {
        // Te dejaria sin poder deshacerlo vos mismo (suspenderte, o sacarte el
        // rol que te deja entrar a /admin/**).
        SELF,
        // /admin/** deja entrar a cualquier admin: sin esto, uno podria dejar
        // afuera a los demas. Solo un superadmin puede.
        ADMIN_TARGET
    }

    private final Reason reason;

    public InvalidAdminActionException(final Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
