// Resultado de sacarle el rol ADMIN a un usuario (ver UserService.revokeAdmin()).
package ar.edu.itba.paw.services;

public enum RevokeAdminResult {
    // Quedo sin el rol ADMIN (tambien si ya no lo tenia: el resultado buscado es el mismo).
    OK,

    // Nadie se saca el rol ADMIN a si mismo: un superadmin que se lo sacara quedaria afuera de
    // /admin/** sin forma de deshacerlo. Regla de negocio, no de acceso (quien puede invocar esto,
    // solo un superadmin, lo decide WebAuthConfig).
    OWN_ACCOUNT,

    // Un superadmin tiene que conservar ADMIN: como no hay herencia de roles, sin ADMIN quedaria afuera de
    // /admin/**. No se le saca el rol (ni se cierra su sesion).
    TARGET_SUPERADMIN,

    // El usuario no existe.
    NOT_FOUND
}
