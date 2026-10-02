package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.ReportResolutionResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.RequestChangesForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        final List<Long> requestedByIds = reports.stream()
                .map(EventReport::getChangesRequestedByUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, User> requestedByUsersById = userService.findByIds(requestedByIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        mav.addObject("requestedByUsersById", requestedByUsersById);
        return mav;
    }

    // Una sola consulta por relacion (findByIds(), ver EventService/UserService)
    // en vez de un findById() por cada reporte de la lista. Compartido por
    // reportes() y seguimiento(): las dos pantallas muestran filas de
    // EventReport con el mismo evento/reportero.
    private ModelAndView reportQueueView(final String viewName, final List<EventReport> reports) {
        final List<Long> eventIds = reports.stream().map(EventReport::getEventId).distinct().collect(Collectors.toList());
        final List<Long> reporterIds = reports.stream().map(EventReport::getReporterUserId).distinct().collect(Collectors.toList());
        final Map<Long, Event> eventsById = eventService.findByIds(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));
        final Map<Long, User> reportersById = userService.findByIds(reporterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
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
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        final ReportResolutionResult result = moderationService.dismiss(id, currentUser.getId());
        // NOT_FOUND: otro admin ya lo resolvio mientras este lo tenia abierto.
        // OWN_EVENT: el reporte es sobre un evento propio (conflicto de
        // intereses). En los dos casos se avisa en vez de redirigir en
        // silencio como si hubiera funcionado.
        if (result != ReportResolutionResult.RESOLVED) {
            redirectAttributes.addFlashAttribute("adminReportError", result.name());
        }
        return redirectToOrigin(origen);
    }

    @RequestMapping(value = "/reportes/{id}/dar-de-baja", method = RequestMethod.POST)
    public ModelAndView darDeBaja(@PathVariable final long id,
                                   @RequestParam(value = "suspenderOrganizador", required = false)
                                   final String suspenderOrganizador,
                                   @RequestParam(value = "origen", required = false) final String origen,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        final ReportResolutionResult result =
                moderationService.removeEvent(id, currentUser.getId(), suspenderOrganizador != null);
        if (result != ReportResolutionResult.RESOLVED) {
            redirectAttributes.addFlashAttribute("adminReportError", result.name());
        }
        return redirectToOrigin(origen);
    }

    @RequestMapping(value = "/reportes/{id}/pedir-cambios", method = RequestMethod.POST)
    public ModelAndView pedirCambios(@PathVariable final long id, @Valid final RequestChangesForm form,
                                      final BindingResult errors, @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                      final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("adminReportError", "INVALID");
            return new ModelAndView("redirect:/admin/reportes");
        }
        final ReportResolutionResult result = moderationService.requestChanges(id, currentUser.getId(), form.getMensaje());
        if (result != ReportResolutionResult.RESOLVED) {
            redirectAttributes.addFlashAttribute("adminReportError", result.name());
        }
        return new ModelAndView("redirect:/admin/reportes");
    }

    @RequestMapping(value = "/usuarios", method = RequestMethod.GET)
    public ModelAndView usuarios(@ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final ModelAndView mav = new ModelAndView("admin/usuarios");
        mav.addObject("users", userService.findAll());
        mav.addObject("currentUserId", currentUser.getId());
        mav.addObject("isSuperadmin", currentUser.getRoles().contains("ROLE_SUPERADMIN"));
        return mav;
    }

    @RequestMapping(value = "/usuarios/{id}/suspender", method = RequestMethod.POST)
    public ModelAndView suspender(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        // Nadie se suspende a si mismo: te dejaria sin poder loguearte de nuevo
        // para deshacerlo (la UI ya esconde el boton para tu propia fila, esto
        // es el mismo chequeo por si el POST se manda directo).
        if (id == currentUser.getId()) {
            redirectAttributes.addFlashAttribute("adminError", "SELF");
            return new ModelAndView("redirect:/admin/usuarios");
        }
        // Un admin comun no suspende a otro admin ni al superadmin: /admin/**
        // lo deja entrar a cualquier admin, asi que sin esto uno podria dejar
        // afuera a los demas. Solo un superadmin puede (como con promover/revocar).
        // La UI ya no ofrece el boton en ese caso; esto cubre el POST directo.
        final boolean targetIsStaff = userService.findById(id)
                .map(target -> target.getRoles().contains("ROLE_ADMIN") || target.getRoles().contains("ROLE_SUPERADMIN"))
                .orElse(false);
        if (targetIsStaff && !currentUser.getRoles().contains("ROLE_SUPERADMIN")) {
            redirectAttributes.addFlashAttribute("adminError", "ADMIN_TARGET");
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
    public ModelAndView revocar(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                 final RedirectAttributes redirectAttributes) {
        // Un superadmin no puede sacarse ROLE_ADMIN a si mismo: /admin/** exige
        // ese rol especificamente (ver WebAuthConfig), asi que hacerlo lo dejaria
        // afuera del panel sin ninguna forma de deshacerlo el mismo.
        if (id == currentUser.getId()) {
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
