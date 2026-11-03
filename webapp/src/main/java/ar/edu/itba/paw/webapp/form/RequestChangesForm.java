package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.services.ModerationService;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// Mensaje del admin al pedirle cambios al creador (AdminController.pedirCambios()).
public class RequestChangesForm {

    @NotBlank
    @Size(max = ModerationService.MAX_CHANGES_MESSAGE_LENGTH)
    private String mensaje;

    public String getMensaje() { return mensaje; }
    public void setMensaje(final String mensaje) { this.mensaje = mensaje; }
}
