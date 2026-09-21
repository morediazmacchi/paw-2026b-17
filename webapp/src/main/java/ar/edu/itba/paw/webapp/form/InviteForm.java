// Datos del formulario de invitar gente a un evento privado, validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class InviteForm {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;

    public InviteForm() {
    }

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
}
