package ar.edu.itba.paw.webapp.interceptor;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Pone "currentLanguage" (el idioma resuelto para este pedido, ej. "es"/"en")
 * en el model de TODAS las paginas, para que la navbar resalte el boton del
 * idioma activo (ver navbar.tag) sin que cada controller lo agregue a mano.
 * Registrado en WebConfig.addInterceptors().
 *
 * Mismo patron y mismo motivo que UnreadInboxInterceptor (postHandle, se
 * saltea los "redirect:"), pero sin el chequeo de Principal: el idioma
 * aplica tambien a un visitante anonimo.
 */
public class CurrentLocaleInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(final HttpServletRequest request, final HttpServletResponse response,
                           final Object handler, final ModelAndView mav) {
        if (mav == null) {
            return;
        }
        final String viewName = mav.getViewName();
        if (viewName == null || viewName.startsWith("redirect:")) {
            return;
        }
        mav.addObject("currentLanguage", LocaleContextHolder.getLocale().getLanguage());
    }
}
