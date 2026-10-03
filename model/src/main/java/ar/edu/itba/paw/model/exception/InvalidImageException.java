package ar.edu.itba.paw.model.exception;

/**
 * La imagen subida no se puede guardar (vacia, muy pesada o no es una imagen).
 * Unchecked: si el service la lanza dentro de una transaccion, Spring hace rollback solo.
 * Lleva la clave de i18n del mensaje para que la vista le muestre al usuario que corregir.
 */
public class InvalidImageException extends RuntimeException {

    private final String messageKey;

    public InvalidImageException(final String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
