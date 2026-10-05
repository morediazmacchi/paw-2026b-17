// Datos del formulario de registro, validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.dto.UserRegistrationDTO;
import ar.edu.itba.paw.webapp.validation.PasswordsMatch;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// @PasswordsMatch con los nombres default (password/confirmarPassword): no
// hace falta pasarle los campos, ya se llaman asi.
@PasswordsMatch
public class UserForm {

    @NotBlank
    @Size(max = 150)
    private String nombre;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    @Size(max = 72)
    private String confirmarPassword;

    public UserForm() {
    }

    public String getNombre() { return nombre; }
    public void setNombre(final String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(final String password) { this.password = password; }

    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(final String confirmarPassword) { this.confirmarPassword = confirmarPassword; }

    // redirectAfterConfirm se pasa aparte (no es un campo del form, viene del
    // query param ?redirect de RegisterController): mismo motivo que
    // EventForm.toEvent(creatorEmail, imageIds) recibe datos externos aparte.
    public UserRegistrationDTO toDTO(final String redirectAfterConfirm) {
        final UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setEmail(email);
        dto.setName(nombre);
        dto.setRawPassword(password);
        dto.setRedirectAfterConfirm(redirectAfterConfirm);
        return dto;
    }
}
