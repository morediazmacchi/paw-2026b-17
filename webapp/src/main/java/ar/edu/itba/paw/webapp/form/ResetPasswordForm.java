// Datos del formulario de "elegir nueva contraseña" (link de "olvidé mi
// contraseña"), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.webapp.validation.PasswordsMatch;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// @PasswordsMatch con los nombres default (password/confirmarPassword): no
// hace falta pasarle los campos, ya se llaman asi.
@PasswordsMatch
public class ResetPasswordForm {

    // Viaja oculto en el form: identifica el link, no se re-tipea a mano.
    @NotBlank
    @Size(max = 64)
    private String token;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(max = 72)
    private String confirmarPassword;

    public ResetPasswordForm() {
    }

    public String getToken() { return token; }
    public void setToken(final String token) { this.token = token; }

    public String getPassword() { return password; }
    public void setPassword(final String password) { this.password = password; }

    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(final String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
}
