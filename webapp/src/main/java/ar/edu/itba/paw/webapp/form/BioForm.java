// Datos del formulario de biografia del perfil publico, validados con JSR-303. No lleva @NotBlank: un texto vacio
// borra la biografia (ver UserService.updateBio()).
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.services.UserService;
import javax.validation.constraints.Size;

public class BioForm {

    @Size(max = UserService.MAX_BIO_LENGTH)
    private String bio;

    public BioForm() {
    }

    public String getBio() { return bio; }
    public void setBio(final String bio) { this.bio = bio; }
}
