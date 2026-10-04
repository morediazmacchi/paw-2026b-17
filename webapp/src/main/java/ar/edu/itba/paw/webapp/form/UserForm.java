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

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña tiene que tener entre 8 y 100 caracteres")
    private String password;

    @NotBlank(message = "Tenes que confirmar la contraseña")
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
