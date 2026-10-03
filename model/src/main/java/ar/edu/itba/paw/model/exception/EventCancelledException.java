package ar.edu.itba.paw.model.exception;

/**
 * Un evento cancelado ya no se edita. Unchecked: a diferencia de otros errores
 * de validacion, no hay nada que el usuario pueda corregir en el form, asi que
 * no se re-muestra -- se redirige, como si la edicion ya hubiese terminado.
 */
public class EventCancelledException extends RuntimeException {

    public EventCancelledException(final long id) {
        super("El evento con id " + id + " esta cancelado");
    }
}
