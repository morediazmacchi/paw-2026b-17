// Datos del formulario de "elegir nueva contraseña" (link de "olvidé mi
// contraseña"), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ResetPasswordForm {

    // Viaja oculto en el form: identifica el link, no se re-tipea a mano.
    @NotBlank
    private String token;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña tiene que tener entre 8 y 100 caracteres")
    private String password;

    @NotBlank(message = "Tenes que confirmar la contraseña")
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
