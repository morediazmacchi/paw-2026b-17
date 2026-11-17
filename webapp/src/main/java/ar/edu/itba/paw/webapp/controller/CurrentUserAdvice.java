package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Deja "currentUser" en el Model de TODOS los controllers (un ControllerAdvice es global), para que cada handler lo
 * reciba con @ModelAttribute sin repetir como se arma.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    // Sale del principal de la sesion (PlancitoUserDetails: id, email y roles, armado una sola vez en el login), sin
    // ir a la base. Es un User "de sesion": solo trae esos tres datos, los que sirven para autorizar; NO tiene nombre
    // (no se autoriza con el y quedaria viejo al editar el perfil): quien lo necesita lo pide al service por id.
    // Sin sesion es null.
    @ModelAttribute("currentUser")
    public User currentUserFromPrincipal(final Authentication authentication) {
        return PlancitoUserDetails.from(authentication).map(CurrentUserAdvice::toUser).orElse(null);
    }

    private static User toUser(final PlancitoUserDetails principal) {
        final User user = new User(principal.getEmail(), null);
        user.setId(principal.getId());
        user.setRoles(Arrays.stream(Role.values())
                .filter(principal::hasRole)
                .collect(Collectors.toCollection(HashSet::new)));
        return user;
    }
}
