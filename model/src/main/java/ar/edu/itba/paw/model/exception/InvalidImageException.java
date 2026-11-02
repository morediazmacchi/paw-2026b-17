package ar.edu.itba.paw.model.exception;

/**
 * La imagen subida no se puede guardar (vacia, muy pesada, no es una imagen) o se subieron mas de las permitidas.
 * Unchecked: si el service la lanza dentro de una transaccion, Spring hace rollback solo.
 * Lleva un codigo de motivo (Reason), NO un mensaje: el texto que ve el usuario (y su idioma) lo elige la capa
 * web a partir del motivo.
 */
public class InvalidImageException extends RuntimeException {

    public enum Reason {
        EMPTY,
        TOO_LARGE,
        NOT_AN_IMAGE,
        TOO_MANY
    }

    private final Reason reason;

    public InvalidImageException(final Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
