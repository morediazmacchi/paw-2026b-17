// Resultado de guardar la biografia desde el perfil publico (UserService.updateBio()).
package ar.edu.itba.paw.services;

public enum UpdateBioResult {
    OK,
    // No existe esa cuenta.
    NOT_FOUND,
    // La biografia supera UserService.MAX_BIO_LENGTH. El form ya lo valida para dar feedback.
    INVALID
}
