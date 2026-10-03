package ar.edu.itba.paw.model.exception;

/**
 * Se pidio un evento que no existe. Extiende RuntimeException (unchecked) a proposito:
 * nadie puede "recuperarse" de que un id no exista, solo mostrar un 404.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(final long id) {
        super("No existe el evento con id " + id);
    }
}
