package ar.edu.itba.paw.model;

/**
 * Algo que tiene roles (User, o su proyeccion UserAccess) y las reglas de "que admin puede suspender a este
 * usuario", en un solo lugar: las usan AdminSecurity (para autorizar) y la pantalla de usuarios (para decidir
 * que botones se ofrecen), asi no hay dos copias.
 */
public interface RoleHolder {

    boolean isAdmin();

    boolean isSuperadmin();

    // A un admin o a un superadmin solo lo suspende un superadmin; al resto, cualquier admin. (Que nadie se
    // suspenda a si mismo es otra regla: AdminSecurity.canSuspend.)
    default boolean canBeSuspendedBy(final boolean callerIsSuperadmin) {
        return callerIsSuperadmin || !(isAdmin() || isSuperadmin());
    }
}
