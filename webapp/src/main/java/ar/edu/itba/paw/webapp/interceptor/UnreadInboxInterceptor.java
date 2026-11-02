package ar.edu.itba.paw.webapp.interceptor;

import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Optional;

/**
 * Pone "unreadInboxCount" (el numerito de la bandeja) en el model de TODAS las
 * paginas, para que la navbar lo muestre sin que cada controller lo agregue a
 * mano. Registrado en WebConfig.addInterceptors().
 *
 * Por que un interceptor y no un @ControllerAdvice con @ModelAttribute: lo que
 * pone un @ModelAttribute tambien va al model de los "redirect:", y Spring los
 * agrega como query params (/eventos?unreadInboxCount=3). Aca, en postHandle, se
 * ve el nombre de la vista y los redirects se saltean.
 *
 * postHandle corre DESPUES del controller: en la propia bandeja, que marca todo
 * como leido, el numerito ya sale en 0.
 */
public class UnreadInboxInterceptor implements HandlerInterceptor {

    private final InboxService inboxService;

    public UnreadInboxInterceptor(final InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @Override
    public void postHandle(final HttpServletRequest request, final HttpServletResponse response,
                           final Object handler, final ModelAndView mav) {
        final Principal principal = request.getUserPrincipal();
        if (mav == null || principal == null) {
            return;
        }
        final String viewName = mav.getViewName();
        if (viewName == null || viewName.startsWith("redirect:")) {
            return;
        }
        // countUnread() es por user_id (un COUNT directo): el id sale del principal de la sesion
        // (PlancitoUserDetails), sin buscar al usuario en la base.
        final Optional<PlancitoUserDetails> user = principal instanceof Authentication
                ? PlancitoUserDetails.from((Authentication) principal)
                : Optional.empty();
        user.ifPresent(u -> mav.addObject("unreadInboxCount", inboxService.countUnread(u.getId())));
    }
}
