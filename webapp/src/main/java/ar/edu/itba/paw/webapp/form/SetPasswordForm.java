// Datos del formulario de "elegir contraseña" (usuario fantasma que intenta
// loguearse), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.dto.UserRegistrationDTO;
import ar.edu.itba.paw.webapp.validation.PasswordsMatch;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

// @PasswordsMatch con los nombres default (password/confirmarPassword): no
// hace falta pasarle los campos, ya se llaman asi.
@PasswordsMatch
public class SetPasswordForm {

    // Viaja oculto en el form (login.jsp ya lo sabe: vino del redirect de
    // LoginFailureHandler). No se re-tipea a mano.
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

    public SetPasswordForm() {
    }

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(final String password) { this.password = password; }

    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(final String confirmarPassword) { this.confirmarPassword = confirmarPassword; }

    // Sin "nombre": este form es solo para un fantasma que ya existe (register()
    // se lo banca, no lo pisa). redirectAfterConfirm se pasa aparte, no es un
    // campo del form.
    public UserRegistrationDTO toDTO(final String redirectAfterConfirm) {
        final UserRegistrationDTO dto = new UserRegistrationDTO();
        dto.setEmail(email);
        dto.setRawPassword(password);
        dto.setRedirectAfterConfirm(redirectAfterConfirm);
        return dto;
    }
}
