// Datos del formulario de "Mis datos" (perfil), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ProfileForm {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    private String nombre;

    // Solo para mostrarlo en la pantalla: el email no se puede cambiar (ver
    // ProfileController.updateProfile), asi que no se valida ni se usa del POST.
    private String email;

    public ProfileForm() {
    }

    public String getNombre() { return nombre; }
    public void setNombre(final String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
}
