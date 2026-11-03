// Resultado de actualizar el nombre desde "Mi perfil" (UserService.updateProfile()).
package ar.edu.itba.paw.services;

public enum UpdateProfileResult {
    OK,
    // No existe esa cuenta.
    NOT_FOUND,
    // El nombre esta vacio o supera UserService.MAX_NAME_LENGTH. El form ya lo valida para dar feedback.
    INVALID_NAME
}
