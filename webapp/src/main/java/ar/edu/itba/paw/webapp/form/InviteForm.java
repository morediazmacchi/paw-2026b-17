// Datos del formulario de invitar gente a un evento privado, validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.services.EventInvitationService;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class InviteForm {

    @NotBlank
    @Email
    @Size(max = EventInvitationService.MAX_EMAIL_LENGTH)
    private String email;

    public InviteForm() {
    }

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
}
