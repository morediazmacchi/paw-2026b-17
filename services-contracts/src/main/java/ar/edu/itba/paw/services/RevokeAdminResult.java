// Resultado de sacarle el rol ADMIN a un usuario (ver UserService.revokeAdmin()).
package ar.edu.itba.paw.services;

public enum RevokeAdminResult {
    // Quedo sin el rol ADMIN (tambien si ya no lo tenia: el resultado buscado es el mismo).
    OK,

    // Un superadmin tiene que conservar ADMIN: como no hay herencia de roles, sin ADMIN quedaria afuera de
    // /admin/**. No se le saca el rol (ni se cierra su sesion).
    TARGET_SUPERADMIN,

    // El usuario no existe.
    NOT_FOUND
}
