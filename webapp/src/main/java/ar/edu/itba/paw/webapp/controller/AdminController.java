package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventApprovalResult;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.ReportResolutionResult;
import ar.edu.itba.paw.services.RevokeAdminResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.RejectEventForm;
import ar.edu.itba.paw.webapp.form.RequestChangesForm;
import ar.edu.itba.paw.webapp.util.Paging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
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

// Panel de administracion
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
    public ModelAndView reportes(@RequestParam(value = "page", required = false) final String page) {
        return reportQueueView("admin/reportes", moderationService.findPendingPage(Paging.parsePage(page)));
    }

    // Panel de seguimiento: reportes
    // donde ya se le pidieron cambios al creador
    @RequestMapping(value = "/reportes/seguimiento", method = RequestMethod.GET)
    public ModelAndView seguimiento(@RequestParam(value = "page", required = false) final String page) {
        final PageResult<EventReport> result = moderationService.findChangesRequestedPage(Paging.parsePage(page));
        final ModelAndView mav = reportQueueView("admin/seguimiento", result);
        final List<Long> requestedByIds = result.getContent().stream()
                .map(EventReport::getChangesRequestedByUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, User> requestedByUsersById = userService.findByIds(requestedByIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        mav.addObject("requestedByUsersById", requestedByUsersById);
        return mav;
    }

    // trae los reports con los datos para mostrarlos en el jsp.
    private ModelAndView reportQueueView(final String viewName, final PageResult<EventReport> result) {
        final List<EventReport> reports = result.getContent();
        final List<Long> eventIds = reports.stream().map(EventReport::getEventId).distinct().collect(Collectors.toList());
        final List<Long> reporterIds = reports.stream().map(EventReport::getReporterUserId).distinct().collect(Collectors.toList());
        final Map<Long, Event> eventsById = eventService.findByIds(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, e -> e));
        final Map<Long, User> reportersById = userService.findByIds(reporterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        final ModelAndView mav = new ModelAndView(viewName);
        mav.addObject("reports", reports);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("eventsById", eventsById);
        mav.addObject("reportersById", reportersById);
        return mav;
    }

    // descartar/dar-de-baja
    private static ModelAndView redirectToOrigin(final String origen) {
        return new ModelAndView("seguimiento".equals(origen)
                ? "redirect:/admin/reportes/seguimiento" : "redirect:/admin/reportes");
    }

    // Resolver un reporte (descartar, pedir cambios, dar de baja): un admin no puede si el evento reportado
    // es suyo (conflicto de intereses). Lo decide @PreAuthorize antes de entrar (ver
    // AdminSecurity.canResolveReport); la pantalla ya esconde los botones en ese caso.
    @PreAuthorize("@adminSecurity.canResolveReport(#id, authentication)")
    @RequestMapping(value = "/reportes/{id}/descartar", method = RequestMethod.POST)
    public ModelAndView descartar(@PathVariable final long id,
                                   @RequestParam(value = "origen", required = false) final String origen,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        final ReportResolutionResult result = moderationService.dismiss(id, currentUser.getId());
        // NOT_FOUND: otro admin ya lo resolvio mientras este lo tenia abierto.
        if (result != ReportResolutionResult.RESOLVED) {
            redirectAttributes.addFlashAttribute("adminReportError", result.name());
        }
        return redirectToOrigin(origen);
    }

    @PreAuthorize("@adminSecurity.canResolveReport(#id, authentication)")
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

    @PreAuthorize("@adminSecurity.canResolveReport(#id, authentication)")
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

    // Revision previa a publicarse (ver EventApprovalStatus): distinto del
    // sistema de reportes de arriba, son eventos que todavia no llegaron a
    // estar publicos.
    @RequestMapping(value = "/eventos-pendientes", method = RequestMethod.GET)
    public ModelAndView eventosPendientes(@RequestParam(value = "page", required = false) final String page) {
        final PageResult<Event> result = eventService.findPendingApprovalPage(Paging.parsePage(page));
        final ModelAndView mav = new ModelAndView("admin/eventosPendientes");
        mav.addObject("pendingEvents", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // Aprobar/rechazar: un admin no puede si el evento es suyo (conflicto de intereses). Lo decide
    // @PreAuthorize antes de entrar (ver AdminSecurity.canModerateEvent); la pantalla ya esconde los botones.
    @PreAuthorize("@adminSecurity.canModerateEvent(#id, authentication)")
    @RequestMapping(value = "/eventos-pendientes/{id}/aprobar", method = RequestMethod.POST)
    public ModelAndView aprobarEvento(@PathVariable final long id, final RedirectAttributes redirectAttributes) {
        final EventApprovalResult result = eventService.approve(id);
        // ALREADY_DECIDED: otro admin ya lo aprobo o rechazo mientras este lo tenia abierto.
        if (result != EventApprovalResult.OK) {
            redirectAttributes.addFlashAttribute("adminApprovalError", result.name());
        }
        return new ModelAndView("redirect:/admin/eventos-pendientes");
    }

    @PreAuthorize("@adminSecurity.canModerateEvent(#id, authentication)")
    @RequestMapping(value = "/eventos-pendientes/{id}/rechazar", method = RequestMethod.POST)
    public ModelAndView rechazarEvento(@PathVariable final long id, @Valid final RejectEventForm form,
                                        final BindingResult errors, final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("adminApprovalError", "INVALID");
            return new ModelAndView("redirect:/admin/eventos-pendientes");
        }
        final EventApprovalResult result = eventService.reject(id, form.getMotivo());
        if (result != EventApprovalResult.OK) {
            redirectAttributes.addFlashAttribute("adminApprovalError", result.name());
        }
        return new ModelAndView("redirect:/admin/eventos-pendientes");
    }

    // Tamaño de pagina para "Usuarios"

    @RequestMapping(value = "/usuarios", method = RequestMethod.GET)
    public ModelAndView usuarios(@RequestParam(value = "page", required = false) final String page,
                                  @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<User> result = userService.findPage(Paging.parsePage(page));

        final ModelAndView mav = new ModelAndView("admin/usuarios");
        mav.addObject("users", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("currentUserId", currentUser.getId());
        mav.addObject("isSuperadmin", currentUser.isSuperadmin());
        return mav;
    }

    // Quien puede suspender a quien (no a si mismo; a otro admin solo un superadmin) lo decide
    // @PreAuthorize antes de entrar (ver AdminSecurity.canSuspend). La pantalla ya esconde el boton
    // cuando no corresponde: esto solo se llega con un POST armado a mano.
    @PreAuthorize("@adminSecurity.canSuspend(#id, authentication)")
    @RequestMapping(value = "/usuarios/{id}/suspender", method = RequestMethod.POST)
    public ModelAndView suspender(@PathVariable final long id) {
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

    // El gate de SUPERADMIN esta en WebAuthConfig (antMatcher de /admin/usuarios/*/revocar). El "no a
    // vos mismo" lo decide el service (RevokeAdminResult.OWN_ACCOUNT): la pantalla ya esconde el boton,
    // esto solo se llega con un POST armado a mano.
    @RequestMapping(value = "/usuarios/{id}/revocar", method = RequestMethod.POST)
    public ModelAndView revocar(@PathVariable final long id,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                final RedirectAttributes redirectAttributes) {
        final RevokeAdminResult result = userService.revokeAdmin(currentUser, id);
        if (result != RevokeAdminResult.OK) {
            redirectAttributes.addFlashAttribute("revokeAdminError", result.name());
        }
        return new ModelAndView("redirect:/admin/usuarios");
    }

    // Un @PreAuthorize denegado en el area de admin responde 403 con la pagina de "sin permiso". En el
    // resto de la app es un 404 (ver ErrorAdvice) para no revelar que un evento privado existe; aca
    // quien actua es un admin y el usuario existe, asi que un 404 solo confundiria.
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView accessDenied() {
        return new ModelAndView("errors/403");
    }

    // Banner
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

    // Moderacion de preguntas
    // borra la pregunta con su respuesta si tenia.
    @RequestMapping(value = "/preguntas/{id}/eliminar", method = RequestMethod.POST)
    public ModelAndView eliminarPregunta(@PathVariable final long id, @RequestParam("eventId") final long eventId) {
        eventQuestionService.deleteByAdmin(id);
        return new ModelAndView("redirect:/eventos/" + eventId + "#preguntas");
    }
}
