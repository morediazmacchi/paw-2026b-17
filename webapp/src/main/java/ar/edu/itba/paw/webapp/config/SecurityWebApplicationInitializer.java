package ar.edu.itba.paw.webapp.config;

import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

// El contenedor Servlet 3.0+ detecta esto solo (via ServletContainerInitializer,
// independiente de que el resto de la app se registre con web.xml) y engancha
// el filtro de Spring Security ("springSecurityFilterChain") delante de todo.
// Sin esta clase, WebAuthConfig compila y arranca, pero ningun filtro
// intercepta nada: todo seguiria siendo publico igual.
public class SecurityWebApplicationInitializer extends AbstractSecurityWebApplicationInitializer {
}
