package ar.edu.itba.paw.model.exception;

/**
 * Un evento rechazado por un admin no se edita: el rechazo es definitivo (no hay reenvio a revision). Igual que
 * EventCancelledException: unchecked, no hay nada que el usuario pueda corregir en el form, asi que no se
 * re-muestra -- se redirige.
 */
public class EventRejectedException extends RuntimeException {
    public EventRejectedException(final long id) {
        super("El evento con id " + id + " fue rechazado y no se puede editar");
    }
}
