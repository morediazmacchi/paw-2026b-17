// Datos del formulario de "Mis datos" (perfil), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ProfileForm {

    @NotBlank
    @Size(max = 150)
    private String nombre;

    public ProfileForm() {
    }

    public String getNombre() { return nombre; }
    public void setNombre(final String nombre) { this.nombre = nombre; }
}
