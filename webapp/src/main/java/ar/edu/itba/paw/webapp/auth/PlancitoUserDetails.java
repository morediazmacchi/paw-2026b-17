package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.Role;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Optional;

// El usuario autenticado, tal como lo guarda Spring Security en la sesion (el "principal").
// Extiende el User de Spring Security para llevar, ademas de email/clave/roles, SOLO lo que hace falta para
// autorizar en cada pedido (el id): asi se lee de memoria en vez de buscar al usuario en la base en cada
// request. Se construye UNA vez, en UserDetailsServiceImpl, durante el login.
//
// A proposito NO lleva datos de presentacion como el nombre: no se autoriza con ellos, y al editarse
// quedarian viejos en la sesion hasta el proximo login. Quien necesita el nombre lo pide al service.
// Tampoco guarda la entidad de dominio User: no es Serializable (la sesion si puede serializarse),
// queda desactualizada y arrastra colecciones de JPA.
//
// Es inmutable (el email es el username, ver getEmail()).
public class PlancitoUserDetails extends User {

    private static final long serialVersionUID = 1L;

    private final long id;

    // El User de Spring no admite una clave null: despues del login Spring la borra, y cuando
    // hay que reconstruir este objeto se pasa "" (no se vuelve a usar para autenticar).
    public PlancitoUserDetails(final long id, final String email, final String passwordHash,
                               final boolean enabled, final Collection<? extends GrantedAuthority> authorities) {
        this(id, email, passwordHash, enabled, true, authorities);
    }

    // enabled = la cuenta esta completa (tiene clave y confirmo el mail); accountNonLocked = un admin NO la suspendio.
    // Son dos cosas distintas a proposito: el login las distingue por el tipo de excepcion (DisabledException =
    // sin confirmar, LockedException = suspendida), sin volver a buscar al usuario en la base.
    public PlancitoUserDetails(final long id, final String email, final String passwordHash,
                               final boolean enabled, final boolean accountNonLocked,
                               final Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, enabled, true, true, accountNonLocked, authorities);
        this.id = id;
    }

    // La cuenta puede entrar: habilitada y no suspendida (el equivalente de la entidad: User.isActive()).
    public boolean isActive() {
        return isEnabled() && isAccountNonLocked();
    }

    // El usuario autenticado de esta sesion, o vacio si no hay uno: sin sesion, no autenticado, o un
    // anonimo (llega "autenticado" con un principal que es solo un String, no un PlancitoUserDetails).
    public static Optional<PlancitoUserDetails> from(final Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        final Object principal = authentication.getPrincipal();
        return principal instanceof PlancitoUserDetails
                ? Optional.of((PlancitoUserDetails) principal)
                : Optional.empty();
    }

    // Tiene ese rol (sale de las authorities que armo el login, ver Role.getAuthority()).
    public boolean hasRole(final Role role) {
        return getAuthorities().stream().anyMatch(authority -> role.getAuthority().equals(authority.getAuthority()));
    }

    public long getId() {
        return id;
    }

    public String getEmail() {
        return getUsername();
    }
}
