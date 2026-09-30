package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.RequestChangesForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
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
    private final EventQuestionService eventQuestionService;

    @Autowired
    public AdminController(final ModerationService moderationService, final UserService userService,
                            final EventService eventService, final EventQuestionService eventQuestionService) {
        this.moderationService = moderationService;
        this.userService = userService;
        this.eventService = eventService;
        this.eventQuestionService = eventQuestionService;
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
        return reportQueueView("admin/reportes", moderationService.listPending());
    }

    // Panel de seguimiento (ver ModerationService.requestChanges()): reportes
    // donde ya se le pidieron cambios al creador, esperando que un admin los
    // revise de nuevo (mismo template de fila que la cola de pendientes, con
    // el mensaje pedido y quien lo pidio ademas del reporte original).
    @RequestMapping(value = "/reportes/seguimiento", method = RequestMethod.GET)
    public ModelAndView seguimiento() {
        final List<EventReport> reports = moderationService.listChangesRequested();
        final ModelAndView mav = reportQueueView("admin/seguimiento", reports);
        final Map<Long, User> requestedByUsersById = new HashMap<>();
        for (final EventReport report : reports) {
            final Long requestedBy = report.getChangesRequestedByUserId();
            if (requestedBy != null && !requestedByUsersById.containsKey(requestedBy)) {
                userService.findById(requestedBy).ifPresent(u -> requestedByUsersById.put(u.getId(), u));
            }
        }
        mav.addObject("requestedByUsersById", requestedByUsersById);
        return mav;
    }

    // Un select en lote por cada relacion (mismo criterio que
    // EventJdbcDao.attachCategories/attachImages): evita un findById por cada
    // reporte de la lista. Compartido por reportes() y seguimiento(): las dos
    // pantallas muestran filas de EventReport con el mismo evento/reportero.
    private ModelAndView reportQueueView(final String viewName, final List<EventReport> reports) {
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
        final ModelAndView mav = new ModelAndView(viewName);
        mav.addObject("reports", reports);
        mav.addObject("eventsById", eventsById);
        mav.addObject("reportersById", reportersById);
        return mav;
    }

    // descartar/dar-de-baja se usan desde las dos pantallas (la cola de
    // pendientes y el panel de seguimiento): "origen" (hidden en el form) dice
    // a cual volver, para no sacar de la pantalla que el admin estaba mirando.
    private static ModelAndView redirectToOrigin(final String origen) {
        return new ModelAndView("seguimiento".equals(origen)
                ? "redirect:/admin/reportes/seguimiento" : "redirect:/admin/reportes");
    }

    @RequestMapping(value = "/reportes/{id}/descartar", method = RequestMethod.POST)
    public ModelAndView descartar(@PathVariable final long id,
                                   @RequestParam(value = "origen", required = false) final String origen,
                                   final Principal principal) {
        moderationService.dismiss(id, currentUser(principal).getId());
        return redirectToOrigin(origen);
    }

    @RequestMapping(value = "/reportes/{id}/dar-de-baja", method = RequestMethod.POST)
    public ModelAndView darDeBaja(@PathVariable final long id,
                                   @RequestParam(value = "suspenderOrganizador", required = false)
                                   final String suspenderOrganizador,
                                   @RequestParam(value = "origen", required = false) final String origen,
                                   final Principal principal) {
        moderationService.removeEvent(id, currentUser(principal).getId(), suspenderOrganizador != null);
        return redirectToOrigin(origen);
    }

    @RequestMapping(value = "/reportes/{id}/pedir-cambios", method = RequestMethod.POST)
    public ModelAndView pedirCambios(@PathVariable final long id, @Valid final RequestChangesForm form,
                                      final BindingResult errors, final Principal principal,
                                      final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("adminReportError", "INVALID");
            return new ModelAndView("redirect:/admin/reportes");
        }
        moderationService.requestChanges(id, currentUser(principal).getId(), form.getMensaje());
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

    // Banner de /eventos (ver EventController.eventos()). Vive aca y no en
    // EventController porque es una accion de admin: bajo /admin/** ya exige
    // ROLE_ADMIN (ver WebAuthConfig), sin repetir el chequeo a mano.
    @RequestMapping(value = "/eventos/{id}/destacar", method = RequestMethod.POST)
    public ModelAndView destacar(@PathVariable final long id) {
        eventService.setFeatured(id, true);
        return new ModelAndView("redirect:/eventos/" + id);
    }

    @RequestMapping(value = "/eventos/{id}/destacar/quitar", method = RequestMethod.POST)
    public ModelAndView quitarDestacado(@PathVariable final long id) {
        eventService.setFeatured(id, false);
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // Moderacion de preguntas (ver EventQuestionService.deleteByAdmin()):
    // borra la pregunta con su respuesta si tenia. eventId viaja en el form
    // (hidden) para poder volver al detalle del evento: la pregunta ya no
    // existe despues de esto, no hay de donde mas sacarlo.
    @RequestMapping(value = "/preguntas/{id}/eliminar", method = RequestMethod.POST)
    public ModelAndView eliminarPregunta(@PathVariable final long id, @RequestParam("eventId") final long eventId) {
        eventQuestionService.deleteByAdmin(id);
        return new ModelAndView("redirect:/eventos/" + eventId + "#preguntas");
    }
}
