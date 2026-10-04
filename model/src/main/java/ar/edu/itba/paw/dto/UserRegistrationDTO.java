// DTO de entrada para registrar un usuario (ver UserForm.toDTO(), SetPasswordForm.toDTO()
// y UserService.register()). Evita pasar varios String seguidos por posicion -- faciles
// de invertir por accidente (email/name/rawPassword son los tres del mismo tipo), como ya
// pasaba en LoginController.setPassword(), que mandaba null a mano para "nombre".
package ar.edu.itba.paw.dto;

public class UserRegistrationDTO {

    private String email;
    private String name;
    private String rawPassword;
    private String redirectAfterConfirm;

    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }

    public String getRawPassword() { return rawPassword; }
    public void setRawPassword(final String rawPassword) { this.rawPassword = rawPassword; }

    public String getRedirectAfterConfirm() { return redirectAfterConfirm; }
    public void setRedirectAfterConfirm(final String redirectAfterConfirm) { this.redirectAfterConfirm = redirectAfterConfirm; }
}
