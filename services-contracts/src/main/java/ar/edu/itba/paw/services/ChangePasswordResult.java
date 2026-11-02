// Resultado de cambiar la contraseña estando logueado (UserService.changePassword()).
package ar.edu.itba.paw.services;

public enum ChangePasswordResult {
    OK,
    // La contraseña actual no coincide.
    WRONG_CURRENT_PASSWORD,
    // La nueva es mas corta que UserService.MIN_PASSWORD_LENGTH.
    WEAK_PASSWORD
}
