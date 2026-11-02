// Resultado de intentar registrar una cuenta propia (email + password).
package ar.edu.itba.paw.services;

public enum UserRegistrationResult {
    CREATED,
    EMAIL_TAKEN,
    // La contraseña es mas corta que UserService.MIN_PASSWORD_LENGTH. El form ya lo valida para dar feedback.
    WEAK_PASSWORD
}
