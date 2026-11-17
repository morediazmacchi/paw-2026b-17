package ar.edu.itba.paw.webapp.interceptor;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.services.AccountSummaryService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Optional;

/**
 * Pone en el model de las paginas de la cuenta (/perfil/**) lo que dibuja la barra lateral: "accountCounts" (los
 * numeritos de "Mis inscripciones", favoritos, eventos e invitaciones) y "user" (nombre y mail de quien mira, leidos de
 * la base: la sesion solo lleva lo que hace falta para autorizar, ver PlancitoUserDetails). Registrado en
 * WebConfig.addInterceptors().
 *
 * Por que un interceptor y no un @ModelAttribute en el controller: un @ModelAttribute corre en CADA pedido del
 * controller, tambien en los POST que terminan en un "redirect:" y no dibujan nada (5 consultas de mas), y lo que
 * pone viaja al model de los redirects. Aca, en postHandle, se ve el nombre de la vista y los redirects se saltean
 * (mismo criterio que UnreadInboxInterceptor).
 *
 * Si el handler ya puso "user" (la pantalla de perfil, que lo necesita para armar su formulario), no se lo vuelve a
 * leer.
 */
public class AccountSidebarInterceptor implements HandlerInterceptor {

    private final AccountSummaryService accountSummaryService;
    private final UserService userService;

    public AccountSidebarInterceptor(final AccountSummaryService accountSummaryService,
                                     final UserService userService) {
        this.accountSummaryService = accountSummaryService;
        this.userService = userService;
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
        final Optional<PlancitoUserDetails> user = principal instanceof Authentication
                ? PlancitoUserDetails.from((Authentication) principal)
                : Optional.empty();
        user.ifPresent(u -> {
            mav.addObject("accountCounts", accountSummaryService.countsFor(u.getId(), u.hasRole(Role.ADMIN)));
            if (!mav.getModel().containsKey("user")) {
                userService.findById(u.getId()).ifPresent(account -> mav.addObject("user", account));
            }
        });
    }
}
