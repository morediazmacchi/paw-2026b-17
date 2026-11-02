package ar.edu.itba.paw.model;

/**
 * Algo que tiene roles (User, o su proyeccion UserAccess) y las reglas de "que admin puede actuar sobre este
 * usuario", en un solo lugar: las usan AdminSecurity (para autorizar) y la pantalla de usuarios (para decidir
 * que botones se ofrecen), asi no hay dos copias.
 */
public interface RoleHolder {

    boolean isAdmin();

    boolean isSuperadmin();

    // A un admin o a un superadmin solo lo suspende un superadmin; al resto, cualquier admin. (Que nadie se
    // suspenda a si mismo es otra regla, del service: UserService.suspend(User, long).)
    default boolean canBeSuspendedBy(final boolean callerIsSuperadmin) {
        return callerIsSuperadmin || !(isAdmin() || isSuperadmin());
    }

    // Dar o sacar el rol ADMIN lo hace solo un superadmin, y nunca sobre otro superadmin (ya es admin y no
    // se le saca el rol). Ver UserService.revokeAdmin().
    default boolean canHaveAdminRoleChangedBy(final boolean callerIsSuperadmin) {
        return callerIsSuperadmin && !isSuperadmin();
    }
}
