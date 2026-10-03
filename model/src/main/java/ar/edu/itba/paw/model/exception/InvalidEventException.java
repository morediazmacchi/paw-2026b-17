package ar.edu.itba.paw.model.exception;

/**
 * Los datos del evento no se pueden guardar: categoria inexistente u otro dato que la base
 * rechaza (field null, viene con la excepcion original de la base como causa), o una regla que
 * el service valida el mismo contra el estado actual en la base -- ej. bajar el cupo por debajo
 * de los lugares ya ocupados (field con el nombre de la propiedad involucrada, sin causa: no hay
 * excepcion de base que envolver). Unchecked. Lleva la clave de i18n del mensaje.
 */
public class InvalidEventException extends RuntimeException {

    private final String messageKey;
    private final String field;

    public InvalidEventException(final String messageKey, final Throwable cause) {
        super(messageKey, cause);
        this.messageKey = messageKey;
        this.field = null;
    }

    public InvalidEventException(final String messageKey, final String field) {
        super(messageKey);
        this.messageKey = messageKey;
        this.field = field;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getField() {
        return field;
    }
}
