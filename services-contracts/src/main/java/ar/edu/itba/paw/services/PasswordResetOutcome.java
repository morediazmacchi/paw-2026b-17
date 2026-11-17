// Lo que devuelve UserService.resetPassword(): el resultado y, si la contraseña se cambio, la cuenta (para que quien
// llama pueda dejar iniciada su sesion).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;

public class PasswordResetOutcome {

    private final PasswordResetResult result;
    private final User user;

    private PasswordResetOutcome(final PasswordResetResult result, final User user) {
        this.result = result;
        this.user = user;
    }

    // La contraseña no se cambio (token invalido, contraseña debil).
    public static PasswordResetOutcome rejected(final PasswordResetResult result) {
        return new PasswordResetOutcome(result, null);
    }

    // La contraseña se cambio: user es la cuenta.
    public static PasswordResetOutcome reset(final User user) {
        return new PasswordResetOutcome(PasswordResetResult.OK, user);
    }

    public PasswordResetResult getResult() { return result; }

    // Solo con resultado OK; null en los demas.
    public User getUser() { return user; }
}
