// Datos del formulario de "cambiar contraseña" (usuario ya logueado, desde
// Mi perfil), validados con JSR-303.
package ar.edu.itba.paw.webapp.form;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class ChangePasswordForm {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String currentPassword;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña tiene que tener entre 8 y 100 caracteres")
    private String newPassword;

    @NotBlank(message = "Tenes que confirmar la contraseña")
    private String confirmarPassword;

    public ChangePasswordForm() {
    }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(final String currentPassword) { this.currentPassword = currentPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(final String newPassword) { this.newPassword = newPassword; }

    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(final String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
}
