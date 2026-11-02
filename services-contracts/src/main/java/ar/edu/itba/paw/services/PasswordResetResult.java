// Resultado de intentar restablecer una contraseña con un token de "olvidé mi contraseña".
package ar.edu.itba.paw.services;

public enum PasswordResetResult {
    OK,
    INVALID_TOKEN,
    // La contraseña nueva es mas corta que UserService.MIN_PASSWORD_LENGTH.
    WEAK_PASSWORD
}
