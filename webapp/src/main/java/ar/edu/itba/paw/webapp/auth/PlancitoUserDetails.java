package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.Role;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Optional;

// El usuario autenticado, tal como lo guarda Spring Security en la sesion (el "principal").
// Extiende el User de Spring Security para llevar, ademas de email/clave/roles, los datos que
// hacen falta en cada pedido (id y nombre): asi se leen de memoria en vez de buscar al usuario
// en la base en cada request. Se construye UNA vez, en UserDetailsServiceImpl, durante el login.
//
// A proposito NO guarda la entidad de dominio User: no es Serializable (la sesion si puede
// serializarse), queda desactualizada y arrastra colecciones de JPA.
//
// Es inmutable: si el usuario cambia su nombre, se reemplaza la autenticacion de la sesion por
// una nueva con el nombre actualizado (el email es el username, ver getEmail()).
public class PlancitoUserDetails extends User {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String name;

    // El User de Spring no admite una clave null: despues del login Spring la borra, y cuando
    // hay que reconstruir este objeto se pasa "" (no se vuelve a usar para autenticar).
    public PlancitoUserDetails(final long id, final String email, final String name, final String passwordHash,
                               final boolean enabled, final Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, enabled, true, true, true, authorities);
        this.id = id;
        this.name = name;
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

    public String getName() {
        return name;
    }
}
