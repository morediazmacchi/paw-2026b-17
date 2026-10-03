package ar.edu.itba.paw.model.exception;

/**
 * Los datos del evento no se pueden guardar (categoria inexistente u otro dato que la base rechaza).
 * Unchecked. Lleva la clave de i18n del mensaje y, como causa, la excepcion original de la base.
 */
public class InvalidEventException extends RuntimeException {

    private final String messageKey;

    public InvalidEventException(final String messageKey, final Throwable cause) {
        super(messageKey, cause);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
