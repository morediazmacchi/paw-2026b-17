package ar.edu.itba.paw.model.exception;

/**
 * Un evento que ya paso no se edita (ya sucedio: cambiarle la fecha, el lugar o el cupo no tiene sentido). Mismo
 * tratamiento que EventCancelledException: no hay nada que corregir en el form, se redirige a "Mis eventos".
 */
public class EventFinishedException extends RuntimeException {

    public EventFinishedException(final long id) {
        super("El evento con id " + id + " ya finalizo");
    }
}
