package ar.edu.itba.paw.webapp.config;

import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

// El contenedor Servlet 3.0+ detecta esto solo (via ServletContainerInitializer,
// independiente de que el resto de la app se registre con web.xml) y engancha
// el filtro de Spring Security ("springSecurityFilterChain") delante de todo.
// Sin esta clase, WebAuthConfig compila y arranca, pero ningun filtro
// intercepta nada: todo seguiria siendo publico igual.
public class SecurityWebApplicationInitializer extends AbstractSecurityWebApplicationInitializer {

    // Registra HttpSessionEventPublisher (sin tocar web.xml): avisa al SessionRegistry cuando una
    // sesion termina (logout, timeout) o cambia de id (al loguearse). Sin esto el registro nunca
    // olvida las sesiones muertas y se pierde el rastro de las que cambian de id.
    @Override
    protected boolean enableHttpSessionEventPublisher() {
        return true;
    }
}
