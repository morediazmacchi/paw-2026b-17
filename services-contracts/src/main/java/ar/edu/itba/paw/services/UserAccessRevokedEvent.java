package ar.edu.itba.paw.services;

// Hecho de dominio: a un usuario se le quito acceso (lo suspendieron, o le sacaron el rol ADMIN), asi
// que las sesiones que tenga abiertas ya no valen. Lo publica UserServiceImpl DENTRO de su transaccion;
// la web lo escucha despues del commit y cierra esas sesiones (ver SessionExpiringListener, webapp.auth).
// Es un POJO sin ninguna dependencia web: el service anuncia lo que paso y no conoce sesiones.
public class UserAccessRevokedEvent {

    private final long userId;

    public UserAccessRevokedEvent(final long userId) {
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }
}
