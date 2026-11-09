package ar.edu.itba.paw.model.exception;

/**
 * Se pidio el perfil de una persona que no existe (o que no se puede mostrar). Extiende RuntimeException
 * (unchecked) a proposito, igual que EventNotFoundException: solo hay que mostrar un 404.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(final long id) {
        super("No existe el usuario con id " + id);
    }
}
