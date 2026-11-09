// Resultado de cambiar o quitar la foto de portada del perfil publico (UserService.updateCover() / removeCover()).
package ar.edu.itba.paw.services;

public enum UpdateCoverResult {
    OK,
    // No existe esa cuenta.
    NOT_FOUND
}
