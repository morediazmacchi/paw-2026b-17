// Datos del formulario de "Mis datos" (perfil), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.services.UserService;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ProfileForm {

    @NotBlank
    @Size(max = UserService.MAX_NAME_LENGTH)
    private String nombre;

    public ProfileForm() {
    }

    public String getNombre() { return nombre; }
    public void setNombre(final String nombre) { this.nombre = nombre; }
}
