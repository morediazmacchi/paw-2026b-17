package ar.edu.itba.paw.webapp.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// Denegacion a nivel FILTRO (un usuario logueado sin el rol que pide un antMatcher, ej. un usuario comun en
// /admin/**): responde EXACTAMENTE lo mismo que cualquier otra cosa a la que no se pueda acceder (un evento
// ajeno, una accion de @PreAuthorize, una URL que no existe): 404 con la pagina generica. No se revela si la
// pagina existe ni por que se niega el acceso. El forward va como GET: con el verbo original (PUT/DELETE/PATCH)
// el motor de JSP rechaza renderizar la pagina.
//
// Las denegaciones de @PreAuthorize las atrapa antes ErrorAdvice (misma respuesta) y nunca llegan hasta aca.
public class AccessDeniedAsNotFoundHandler implements AccessDeniedHandler {

    private final String notFoundPage;

    public AccessDeniedAsNotFoundHandler(final String notFoundPage) {
        this.notFoundPage = notFoundPage;
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response,
                       final AccessDeniedException accessDeniedException) throws IOException, ServletException {
        request.setAttribute(WebAttributes.ACCESS_DENIED_403, accessDeniedException);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        request.getRequestDispatcher(notFoundPage).forward(new AsGetRequest(request), response);
    }

    // El mismo pedido, pero que dice ser un GET.
    static class AsGetRequest extends HttpServletRequestWrapper {

        AsGetRequest(final HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getMethod() {
            return "GET";
        }
    }
}
