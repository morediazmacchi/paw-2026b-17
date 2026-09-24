package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.InvitationResult;
import ar.edu.itba.paw.services.WaitlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Date;

/**
 * La invitacion de lista de espera que se abre desde el mail.
 *
 * El link solo dice de QUE evento se trata. QUIEN sos lo dice la sesion: estas
 * rutas piden estar logueado (anyRequest().authenticated() en WebAuthConfig; si
 * no lo estas, Spring te manda al login y despues te devuelve aca). Si el mail
 * se reenvia, el otro entra con su cuenta y no encuentra ninguna invitacion.
 */
@Controller
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Autowired
    public WaitlistController(final WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    /**
     * SOLO muestra. No cambia nada a proposito: los antivirus de correo y varios
     * clientes de mail abren los links para escanearlos, asi que un GET que
     * aceptara la invitacion se ejecutaria solo, sin que la persona haga nada.
     * Aceptar y rechazar son POST, abajo.
     */
    @RequestMapping(value = "/invitacion/{eventId}", method = RequestMethod.GET)
    public ModelAndView show(@PathVariable final long eventId,
                             @RequestParam(value = "resultado", required = false) final String resultado,
                             final Principal principal) {
        final ModelAndView mav = new ModelAndView("waitlist/invitacion");
        mav.addObject("eventId", eventId);
        mav.addObject("resultado", resultado);

        final Date now = new Date();
        waitlistService.findForUser(eventId, principal.getName()).ifPresent(detail -> {
            mav.addObject("event", detail.getEvent());
            mav.addObject("invitation", detail.getInvitation());
            mav.addObject("pending", detail.getInvitation().isPending());
            mav.addObject("expired", detail.getInvitation().isExpiredAt(now));
            mav.addObject("eventPassed", detail.getEvent().getEventDate() != null
                    && detail.getEvent().getEventDate().before(now));
        });
        return mav;
    }

    @RequestMapping(value = "/invitacion/{eventId}/aceptar", method = RequestMethod.POST)
    public ModelAndView accept(@PathVariable final long eventId, final Principal principal) {
        return redirectWith(eventId, waitlistService.accept(eventId, principal.getName()));
    }

    @RequestMapping(value = "/invitacion/{eventId}/rechazar", method = RequestMethod.POST)
    public ModelAndView decline(@PathVariable final long eventId, final Principal principal) {
        return redirectWith(eventId, waitlistService.decline(eventId, principal.getName()));
    }

    // POST-Redirect-GET, igual que publicar un evento: lo ultimo que hizo el
    // navegador es un GET, asi que F5 no vuelve a contestar la invitacion.
    private static ModelAndView redirectWith(final long eventId, final InvitationResult result) {
        return new ModelAndView("redirect:/invitacion/" + eventId + "?resultado=" + result.name().toLowerCase());
    }
}
