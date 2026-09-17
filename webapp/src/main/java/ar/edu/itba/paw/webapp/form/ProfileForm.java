// Datos del formulario de "Mis datos" (perfil), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ProfileForm {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;

    public ProfileForm() {
    }

    public String getNombre() { return nombre; }
    public void setNombre(final String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
}
