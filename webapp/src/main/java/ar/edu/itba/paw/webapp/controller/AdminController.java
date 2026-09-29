package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Panel de administracion (PAW20-67): cola de reportes y gestion de usuarios.
// Todo bajo /admin/**, ya protegido por WebAuthConfig: hasRole("ADMIN") para
// todo lo de aca, y hasRole("SUPERADMIN") especificamente para promover/
// revocar admin (ver los antMatchers, van ANTES del general). Esta clase no
// vuelve a chequear el rol: si llego hasta aca, ya esta autorizado.
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ModerationService moderationService;
    private final UserService userService;
    private final EventService eventService;

    @Autowired
    public AdminController(final ModerationService moderationService, final UserService userService,
                            final EventService eventService) {
        this.moderationService = moderationService;
        this.userService = userService;
        this.eventService = eventService;
    }

    private User currentUser(final Principal principal) {
        return userService.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado sin fila en la base: " + principal.getName()));
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ModelAndView index() {
        return new ModelAndView("redirect:/admin/reportes");
    }

    @RequestMapping(value = "/reportes", method = RequestMethod.GET)
    public ModelAndView reportes() {
        final List<EventReport> reports = moderationService.listPending();
        // Un select en lote por cada relacion (mismo criterio que
        // EventJdbcDao.attachCategories/attachImages): evita un findById por
        // cada reporte de la cola.
        final Map<Long, Event> eventsById = new HashMap<>();
        final Map<Long, User> reportersById = new HashMap<>();
        for (final EventReport report : reports) {
            if (!eventsById.containsKey(report.getEventId())) {
                eventService.findById(report.getEventId()).ifPresent(e -> eventsById.put(e.getId(), e));
            }
            if (!reportersById.containsKey(report.getReporterUserId())) {
                userService.findById(report.getReporterUserId()).ifPresent(u -> reportersById.put(u.getId(), u));
            }
        }
        final ModelAndView mav = new ModelAndView("admin/reportes");
        mav.addObject("reports", reports);
        mav.addObject("eventsById", eventsById);
        mav.addObject("reportersById", reportersById);
        return mav;
    }

    @RequestMapping(value = "/reportes/{id}/descartar", method = RequestMethod.POST)
    public ModelAndView descartar(@PathVariable final long id, final Principal principal) {
        moderationService.dismiss(id, currentUser(principal).getId());
        return new ModelAndView("redirect:/admin/reportes");
    }

    @RequestMapping(value = "/reportes/{id}/dar-de-baja", method = RequestMethod.POST)
    public ModelAndView darDeBaja(@PathVariable final long id,
                                   @RequestParam(value = "suspenderOrganizador", required = false)
                                   final String suspenderOrganizador,
                                   final Principal principal) {
        moderationService.removeEvent(id, currentUser(principal).getId(), suspenderOrganizador != null);
        return new ModelAndView("redirect:/admin/reportes");
    }

    @RequestMapping(value = "/usuarios", method = RequestMethod.GET)
    public ModelAndView usuarios(final Principal principal) {
        final ModelAndView mav = new ModelAndView("admin/usuarios");
        mav.addObject("users", userService.findAll());
        mav.addObject("currentUserId", currentUser(principal).getId());
        mav.addObject("isSuperadmin", currentUser(principal).getRoles().contains("ROLE_SUPERADMIN"));
        return mav;
    }

    @RequestMapping(value = "/usuarios/{id}/suspender", method = RequestMethod.POST)
    public ModelAndView suspender(@PathVariable final long id, final Principal principal,
                                   final RedirectAttributes redirectAttributes) {
        // Nadie se suspende a si mismo: te dejaria sin poder loguearte de nuevo
        // para deshacerlo (la UI ya esconde el boton para tu propia fila, esto
        // es el mismo chequeo por si el POST se manda directo).
        if (id == currentUser(principal).getId()) {
            redirectAttributes.addFlashAttribute("adminError", "SELF");
            return new ModelAndView("redirect:/admin/usuarios");
        }
        userService.suspend(id);
        return new ModelAndView("redirect:/admin/usuarios");
    }

    @RequestMapping(value = "/usuarios/{id}/reactivar", method = RequestMethod.POST)
    public ModelAndView reactivar(@PathVariable final long id) {
        userService.reactivate(id);
        return new ModelAndView("redirect:/admin/usuarios");
    }

    @RequestMapping(value = "/usuarios/{id}/promover", method = RequestMethod.POST)
    public ModelAndView promover(@PathVariable final long id) {
        userService.grantAdmin(id);
        return new ModelAndView("redirect:/admin/usuarios");
    }

    @RequestMapping(value = "/usuarios/{id}/revocar", method = RequestMethod.POST)
    public ModelAndView revocar(@PathVariable final long id, final Principal principal,
                                 final RedirectAttributes redirectAttributes) {
        // Un superadmin no puede sacarse ROLE_ADMIN a si mismo: /admin/** exige
        // ese rol especificamente (ver WebAuthConfig), asi que hacerlo lo dejaria
        // afuera del panel sin ninguna forma de deshacerlo el mismo.
        if (id == currentUser(principal).getId()) {
            redirectAttributes.addFlashAttribute("adminError", "SELF");
            return new ModelAndView("redirect:/admin/usuarios");
        }
        userService.revokeAdmin(id);
        return new ModelAndView("redirect:/admin/usuarios");
    }
}
