// Datos del formulario de "olvidé mi contraseña", validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.Size;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

public class ForgotPasswordForm {

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    public ForgotPasswordForm() {
    }

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
}
