package ar.edu.itba.paw.model;

/**
 * Los roles que puede tener un usuario (correccion 13). Antes eran Strings sueltos
 * ("ROLE_ADMIN", ...): aceptaban cualquier valor y el literal se repetia por todo el codigo.
 *
 * En la base (tabla user_role) se guarda el nombre tal cual: USER, ADMIN, SUPERADMIN.
 * Spring Security espera el prefijo "ROLE_": ese mapeo vive solo aca, en getAuthority().
 */
public enum Role {
    // Toda cuenta registrada.
    USER,
    // Modera eventos, reportes y usuarios (ver WebAuthConfig: /admin/**).
    ADMIN,
    // Ademas de ADMIN (la cuenta semilla tiene los dos), puede dar y sacar ADMIN a otros.
    SUPERADMIN;

    // El nombre que usa Spring Security: hasRole("ADMIN") busca la autoridad "ROLE_ADMIN".
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
