package ar.edu.itba.paw.services;

// Hecho de dominio: cambio la contraseña de un usuario, asi que las sesiones que ya tenia abiertas (una
// robada, una que quedo abierta en otra PC) tienen que dejar de valer. Lo publica UserServiceImpl DENTRO
// de su transaccion; la web lo escucha despues del commit y cierra esas sesiones (ver
// SessionExpiringListener, webapp.auth). Es un POJO sin ninguna dependencia web: el service anuncia lo que
// paso y no conoce sesiones.
//
// keepCurrentSession: true cuando la persona cambia la clave estando logueada (su sesion actual tiene que
// seguir viva, solo se cierran las OTRAS); false cuando restablece la clave por el link del mail (se
// cierran todas).
public class UserCredentialsChangedEvent {

    private final long userId;
    private final boolean keepCurrentSession;

    public UserCredentialsChangedEvent(final long userId, final boolean keepCurrentSession) {
        this.userId = userId;
        this.keepCurrentSession = keepCurrentSession;
    }

    public long getUserId() {
        return userId;
    }

    public boolean isKeepCurrentSession() {
        return keepCurrentSession;
    }
}
