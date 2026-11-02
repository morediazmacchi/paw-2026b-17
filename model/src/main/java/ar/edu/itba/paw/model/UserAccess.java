package ar.edu.itba.paw.model;

import java.util.Collections;
import java.util.Set;

// Lo minimo de un usuario para decidir un permiso sobre el (ver AdminSecurity: canSuspend): su id y sus roles. Es una proyeccion (no una entidad): se arma sin cargar el
// usuario, asi que no trae la contrasena ni los demas datos. La devuelve UserDao.findAccessById().
public class UserAccess {

    private final long id;
    private final Set<Role> roles;

    public UserAccess(final long id, final Set<Role> roles) {
        this.id = id;
        this.roles = Collections.unmodifiableSet(roles);
    }

    public long getId() {
        return id;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    public boolean isSuperadmin() {
        return roles.contains(Role.SUPERADMIN);
    }
}
