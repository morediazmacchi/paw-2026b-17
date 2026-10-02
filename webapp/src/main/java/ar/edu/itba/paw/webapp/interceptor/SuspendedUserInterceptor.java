package ar.edu.itba.paw.webapp.interceptor;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;

/**
 * Corta la sesion de una cuenta que un admin suspendio mientras estaba logueada.
 *
 * El chequeo de "suspendida" de UserDetailsServiceImpl corre solo al LOGUEARSE:
 * una sesion que ya estaba abierta (o creada justo antes de la suspension)
 * seguiria funcionando hasta que la cookie venza. Aca se vuelve a mirar la
 * cuenta en cada pedido a un controller y, si esta suspendida, se cierra la
 * sesion y se manda a /login?suspended (el mismo aviso que ve quien intenta
 * entrar). Registrado en WebConfig.addInterceptors().
 *
 * Los archivos estaticos (/css, /js) no son un controller y se saltean: no
 * tiene sentido ir a la base por cada uno.
 */
public class SuspendedUserInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuspendedUserInterceptor.class);

    private final UserService userService;

    public SuspendedUserInterceptor(final UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
                             final Object handler) throws IOException {
        final Principal principal = request.getUserPrincipal();
        if (principal == null || !(handler instanceof HandlerMethod)) {
            return true;
        }
        final Optional<User> user = userService.findByEmail(principal.getName());
        if (!user.isPresent() || !user.get().isSuspended()) {
            return true;
        }
        LOGGER.info("Se cierra la sesion de la cuenta {}: fue suspendida", user.get().getId());
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        response.sendRedirect(request.getContextPath() + "/login?suspended");
        return false;
    }
}
