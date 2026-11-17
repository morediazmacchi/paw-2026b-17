package ar.edu.itba.paw.webapp.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Deja iniciada la sesion de alguien que acaba de demostrar que es el dueño de la cuenta sin pasar por el formulario
 * de login: confirmo el mail (link de confirmacion) o restablecio la contraseña (link de recuperacion). Arma la
 * Authentication a mano y la guarda en la sesion HTTP, como hace el login normal.
 */
@Component
public class AutoLogin {

    private final UserDetailsServiceImpl userDetailsService;
    // El mismo registro de WebAuthConfig: el login normal lo alimenta solo (sessionManagement), pero aca la sesion se
    // arma a mano y hay que registrarla igual.
    private final SessionRegistry sessionRegistry;

    @Autowired
    public AutoLogin(final UserDetailsServiceImpl userDetailsService, final SessionRegistry sessionRegistry) {
        this.userDetailsService = userDetailsService;
        this.sessionRegistry = sessionRegistry;
    }

    /** false si la cuenta no esta activa (suspendida o sin confirmar): en ese caso no se inicia nada. */
    public boolean login(final String email, final HttpServletRequest request) {
        final PlancitoUserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isActive()) {
            return false;
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        final UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        // Al armar la sesion a mano no pasa por la estrategia de Spring Security que la registra: sin esto,
        // SessionExpiringListener no la encuentra y una cuenta que despues suspenden conserva su sesion hasta el
        // timeout. Se registra con el MISMO PlancitoUserDetails que lleva la Authentication, para que el listener lo
        // encuentre por getId().
        sessionRegistry.registerNewSession(session.getId(), userDetails);
        return true;
    }
}
