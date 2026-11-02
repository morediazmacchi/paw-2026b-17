package ar.edu.itba.paw.model.exception;

/**
 * Los datos del evento no se pueden guardar: un dato que la base rechaza (field null, viene con la excepcion
 * original de la base como causa), o una regla que el service valida contra los datos que recibe o contra el
 * estado actual en la base -- ej. bajar el cupo por debajo de los lugares ya ocupados (field con el nombre de
 * la propiedad involucrada, sin causa: no hay excepcion de base que envolver). Unchecked.
 * Lleva un codigo de motivo (Reason), NO un mensaje: el texto que ve el usuario (y su idioma) lo elige la capa
 * web a partir del motivo.
 */
public class InvalidEventException extends RuntimeException {

    public enum Reason {
        // La base rechazo algun dato (largo, formato...).
        INVALID_DATA,
        CATEGORY_NOT_FOUND,
        NO_CATEGORY,
        // El cupo nuevo es menor a los lugares ya ocupados.
        CAPACITY_BELOW_OCCUPIED,
        DATE_IN_PAST,
        DATE_TOO_FAR
    }

    private final Reason reason;
    private final String field;

    public InvalidEventException(final Reason reason, final Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
        this.field = null;
    }

    public InvalidEventException(final Reason reason, final String field) {
        super(reason.name());
        this.reason = reason;
        this.field = field;
    }

    public Reason getReason() {
        return reason;
    }

    // La propiedad del form involucrada, o null si el motivo no es de un campo en particular.
    public String getField() {
        return field;
    }
}
