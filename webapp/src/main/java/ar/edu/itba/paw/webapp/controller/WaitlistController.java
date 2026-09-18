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

import java.util.Date;

/**
 * La invitacion de lista de espera que se abre desde el mail.
 *
 * Estas tres rutas son publicas (ver WebAuthConfig): quien recibe el mail puede
 * estar deslogueado, o logueado con otra cuenta, o abrirlo desde el celular. El
 * TOKEN es la credencial: tenerlo prueba que sos quien recibio ese mail, porque
 * no hay otra forma de haberlo conseguido.
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
    @RequestMapping(value = "/invitacion/{token}", method = RequestMethod.GET)
    public ModelAndView show(@PathVariable final String token,
                             @RequestParam(value = "resultado", required = false) final String resultado) {
        final ModelAndView mav = new ModelAndView("waitlist/invitacion");
        mav.addObject("token", token);
        mav.addObject("resultado", resultado);

        final Date now = new Date();
        waitlistService.findByToken(token).ifPresent(detail -> {
            mav.addObject("event", detail.getEvent());
            mav.addObject("invitation", detail.getInvitation());
            mav.addObject("pending", detail.getInvitation().isPending());
            mav.addObject("expired", detail.getInvitation().isExpiredAt(now));
            mav.addObject("eventPassed", detail.getEvent().getEventDate() != null
                    && detail.getEvent().getEventDate().before(now));
        });
        return mav;
    }

    @RequestMapping(value = "/invitacion/{token}/aceptar", method = RequestMethod.POST)
    public ModelAndView accept(@PathVariable final String token) {
        return redirectWith(token, waitlistService.accept(token));
    }

    @RequestMapping(value = "/invitacion/{token}/rechazar", method = RequestMethod.POST)
    public ModelAndView decline(@PathVariable final String token) {
        return redirectWith(token, waitlistService.decline(token));
    }

    // POST-Redirect-GET, igual que publicar un evento: lo ultimo que hizo el
    // navegador es un GET, asi que F5 no vuelve a contestar la invitacion.
    private static ModelAndView redirectWith(final String token, final InvitationResult result) {
        return new ModelAndView("redirect:/invitacion/" + token + "?resultado=" + result.name().toLowerCase());
    }
}
