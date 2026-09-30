package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// Mensaje del admin al pedirle cambios al creador (AdminController.pedirCambios()).
public class RequestChangesForm {

    @NotBlank
    @Size(max = 1000)
    private String mensaje;

    public String getMensaje() { return mensaje; }
    public void setMensaje(final String mensaje) { this.mensaje = mensaje; }
}
