package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.services.EventApprovalResult;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.EventWithRegistrationStats;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReportQueuePage;
import ar.edu.itba.paw.services.ReportResolutionResult;
import ar.edu.itba.paw.services.RevokeAdminResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.RejectEventForm;
import ar.edu.itba.paw.webapp.form.RequestChangesForm;
import ar.edu.itba.paw.webapp.util.Paging;
import ar.edu.itba.paw.webapp.util.UserListLink;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.Map;
import java.util.LinkedHashMap;

// Panel de administracion
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ModerationService moderationService;
    private final UserService userService;
    private final EventService eventService;
    private final EventQuestionService eventQuestionService;
    private final RegistrationService registrationService;

    @Autowired
    public AdminController(final ModerationService moderationService, final UserService userService,
                            final EventService eventService, final EventQuestionService eventQuestionService,
                            final RegistrationService registrationService) {
        this.moderationService = moderationService;
        this.userService = userService;
        this.eventService = eventService;
        this.eventQuestionService = eventQuestionService;
        this.registrationService = registrationService;
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ModelAndView index() {
        return new ModelAndView("redirect:/admin/reportes");
    }

    @RequestMapping(value = "/reportes", method = RequestMethod.GET)
    public ModelAndView reportes(@RequestParam(value = "page", required = false) final String page) {
        final ReportQueuePage queue = moderationService.findPendingQueue(Paging.parsePage(page));
        return reportQueueView("admin/reportes", queue);
    }

    // Panel de seguimiento: reportes
    // donde ya se le pidieron cambios al creador
    @RequestMapping(value = "/reportes/seguimiento", method = RequestMethod.GET)
    public ModelAndView seguimiento(@RequestParam(value = "page", required = false) final String page) {
        final ReportQueuePage queue = moderationService.findChangesRequestedQueue(Paging.parsePage(page));
        final ModelAndView mav = reportQueueView("admin/seguimiento", queue);
        mav.addObject("requestedByUsersById", queue.getRequestedByUsersById());
        return mav;
    }

    // Los reports con los datos para mostrarlos en el jsp (los arma el service).
    private ModelAndView reportQueueView(final String viewName, final ReportQueuePage queue) {
        final ModelAndView mav = new ModelAndView(viewName);
        mav.addObject("reports", queue.getReports().getContent());
        mav.addObject("currentPage", queue.getReports().getPage());
        mav.addObject("totalPages", queue.getReports().getTotalPages());
        mav.addObject("eventsById", queue.getEventsById());
        mav.addObject("reportersById", queue.getReportersById());
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

    // Revision previa a publicarse (ver EventApprovalStatus): distinto del sistema de reportes de arriba, son eventos
    // que todavia no llegaron a estar publicos. Se aprueban, rechazan o se les piden cambios desde la lista de todos
    // los eventos.
    // Aprobar/rechazar: un admin no puede si el evento es suyo (conflicto de intereses). Lo decide
    // @PreAuthorize antes de entrar (ver AdminSecurity.canModerateEvent); la pantalla ya esconde los botones.
    @PreAuthorize("@adminSecurity.canModerateEvent(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/aprobar", method = RequestMethod.POST)
    public ModelAndView aprobarEvento(@PathVariable final long id, final RedirectAttributes redirectAttributes) {
        final EventApprovalResult result = eventService.approve(id);
        // ALREADY_DECIDED: otro admin ya lo aprobo o rechazo mientras este lo tenia abierto.
        if (result != EventApprovalResult.OK) {
            redirectAttributes.addFlashAttribute("adminApprovalError", result.name());
        }
        return new ModelAndView("redirect:/admin/eventos");
    }

    @PreAuthorize("@adminSecurity.canModerateEvent(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/rechazar", method = RequestMethod.POST)
    public ModelAndView rechazarEvento(@PathVariable final long id, @Valid final RejectEventForm form,
                                        final BindingResult errors, final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("adminApprovalError", "INVALID");
            return new ModelAndView("redirect:/admin/eventos");
        }
        final EventApprovalResult result = eventService.reject(id, form.getMotivo());
        if (result != EventApprovalResult.OK) {
            redirectAttributes.addFlashAttribute("adminApprovalError", result.name());
        }
        return new ModelAndView("redirect:/admin/eventos");
    }

    // No es definitivo como rechazar: el evento sigue vivo, el creador lo puede editar y vuelve
    // solo a PENDING (a revision) cuando lo hace (ver EventService.update()/EventServiceImpl.requestChanges()).
    @PreAuthorize("@adminSecurity.canModerateEvent(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/pedir-cambios", method = RequestMethod.POST)
    public ModelAndView pedirCambiosEvento(@PathVariable final long id, @Valid final RequestChangesForm form,
                                            final BindingResult errors, final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("adminApprovalError", "INVALID");
            return new ModelAndView("redirect:/admin/eventos");
        }
        final EventApprovalResult result = eventService.requestChanges(id, form.getMensaje());
        if (result != EventApprovalResult.OK) {
            redirectAttributes.addFlashAttribute("adminApprovalError", result.name());
        }
        return new ModelAndView("redirect:/admin/eventos");
    }

    // Tamaño de pagina para "Usuarios"

    @RequestMapping(value = "/usuarios", method = RequestMethod.GET)
    public ModelAndView usuarios(@RequestParam(value = "q", required = false) final String q,
                                  @RequestParam(value = "page", required = false) final String page,
                                  @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<User> result = userService.findPage(q, Paging.parsePage(page));

        final ModelAndView mav = new ModelAndView("admin/usuarios");
        mav.addObject("users", result.getContent());
        // La busqueda por mail: el texto para volver a mostrarlo (la pantalla lo escapa) y cuantos usuarios matchean.
        mav.addObject("q", q == null ? "" : q);
        // Las acciones de cada fila (suspender, promover...) vuelven aca, a la misma busqueda y pagina: ver UserListLink.
        mav.addObject("returnTo", UserListLink.of(q, result.getPage()));
        // Lo que el paginador repite en sus links para que cambiar de pagina conserve la busqueda.
        final Map<String, String> pagerParams = new LinkedHashMap<>();
        if (q != null && !q.trim().isEmpty()) {
            pagerParams.put("q", q.trim());
        }
        mav.addObject("pagerParams", pagerParams);
        mav.addObject("totalElements", result.getTotalElements());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("currentUserId", currentUser.getId());
        return mav;
    }

    // Quien puede suspender a quien (a otro admin solo un superadmin, nadie a si mismo, un usuario que
    // existe) lo decide @PreAuthorize antes de entrar (ver AdminSecurity.canSuspend): la pantalla ya
    // esconde el boton, esto solo se llega con un POST armado a mano.
    @PreAuthorize("@adminSecurity.canSuspend(#id, authentication)")
    @RequestMapping(value = "/usuarios/{id}/suspender", method = RequestMethod.POST)
    public ModelAndView suspender(@PathVariable final long id,
                                  @RequestParam(value = "returnTo", required = false) final String returnTo) {
        userService.suspend(id);
        return new ModelAndView("redirect:" + UserListLink.safeReturnTo(returnTo));
    }

    @RequestMapping(value = "/usuarios/{id}/reactivar", method = RequestMethod.POST)
    public ModelAndView reactivar(@PathVariable final long id,
                                  @RequestParam(value = "returnTo", required = false) final String returnTo) {
        userService.reactivate(id);
        return new ModelAndView("redirect:" + UserListLink.safeReturnTo(returnTo));
    }

    // Dar el rol admin lo puede hacer cualquier admin (lo cubre el "/admin/**" de WebAuthConfig); sacarlo, solo un
    // superadmin (ver revocar() abajo).
    @RequestMapping(value = "/usuarios/{id}/promover", method = RequestMethod.POST)
    public ModelAndView promover(@PathVariable final long id,
                                 @RequestParam(value = "returnTo", required = false) final String returnTo) {
        userService.grantAdmin(id);
        return new ModelAndView("redirect:" + UserListLink.safeReturnTo(returnTo));
    }

    // El gate de SUPERADMIN esta en WebAuthConfig (antMatcher de /admin/usuarios/*/revocar). El "no a
    // vos mismo" lo decide @PreAuthorize antes de entrar (ver AdminSecurity.canRevokeAdmin): la pantalla
    // ya esconde el boton, esto solo se llega con un POST armado a mano.
    @PreAuthorize("@adminSecurity.canRevokeAdmin(#id, authentication)")
    @RequestMapping(value = "/usuarios/{id}/revocar", method = RequestMethod.POST)
    public ModelAndView revocar(@PathVariable final long id,
                                @RequestParam(value = "returnTo", required = false) final String returnTo,
                                final RedirectAttributes redirectAttributes) {
        final RevokeAdminResult result = userService.revokeAdmin(id);
        if (result != RevokeAdminResult.OK) {
            redirectAttributes.addFlashAttribute("revokeAdminError", result.name());
        }
        return new ModelAndView("redirect:" + UserListLink.safeReturnTo(returnTo));
    }


    // Moderacion: ver TODOS los eventos (cualquier estado) y, de cada uno, sus inscriptos; los pendientes de aprobacion
    // se aprueban o rechazan ahi mismo. Es el panel completo para auditar la plataforma. Nadie mas lo necesita: isOwner (ver EventController.inscriptos) sigue siendo la
    // regla para el organizador, esta ruta es exclusiva de /admin/** (role ADMIN en WebAuthConfig).
    @RequestMapping(value = "/eventos", method = RequestMethod.GET)
    public ModelAndView eventos(@RequestParam(value = "page", required = false) final String page,
                                @RequestParam(value = "q", required = false) final String query,
                                @RequestParam(value = "sort", required = false) final String sort) {
        final AdminEventSort effectiveSort = parseEventSort(sort);
        final String trimmedQuery = query == null ? "" : query.trim();
        final PageResult<EventWithRegistrationStats> result =
                eventService.findAllEventsPage(trimmedQuery, effectiveSort, Paging.parsePage(page));
        final ModelAndView mav = new ModelAndView("admin/eventos");
        mav.addObject("events", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("q", trimmedQuery);
        mav.addObject("sort", effectiveSort.name());
        mav.addObject("sortOptions", AdminEventSort.values());
        // Lo que el paginador repite en sus links para que cambiar de pagina conserve la busqueda y el orden.
        final Map<String, String> pagerParams = new LinkedHashMap<>();
        if (!trimmedQuery.isEmpty()) {
            pagerParams.put("q", trimmedQuery);
        }
        pagerParams.put("sort", effectiveSort.name());
        mav.addObject("pagerParams", pagerParams);
        return mav;
    }

    // Un orden que no existe (o ninguno) cae al de por defecto, en vez de dar error.
    private static AdminEventSort parseEventSort(final String value) {
        try {
            return value == null ? AdminEventSort.CREATED_DESC : AdminEventSort.valueOf(value);
        } catch (final IllegalArgumentException e) {
            return AdminEventSort.CREATED_DESC;
        }
    }

    // Inscriptos de un evento cualquiera, de solo lectura: a diferencia de
    // /perfil/eventos/{id}/inscriptos (del organizador, con boton de revocar), este panel es para
    // mirar y moderar, no para administrar inscripciones ajenas.
    @RequestMapping(value = "/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView inscriptos(@PathVariable final long id,
                                   @RequestParam(value = "page", required = false) final String page) {
        final Event event = eventService.findByIdOrThrow(id);
        final PageResult<Registration> result = registrationService.findPageByEventId(id, Paging.parsePage(page));
        final ModelAndView mav = new ModelAndView("admin/eventoInscriptos");
        mav.addObject("event", event);
        mav.addObject("registrations", result.getContent());
        mav.addObject("totalRegistrations", result.getTotalElements());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // Moderacion de preguntas
    // borra la pregunta con su respuesta si tenia.
    @RequestMapping(value = "/preguntas/{id}/eliminar", method = RequestMethod.POST)
    public ModelAndView eliminarPregunta(@PathVariable final long id) {
        // Vuelve a la pagina del evento de la pregunta (lo dice el service, no el cliente); si la pregunta
        // ya no estaba (otro admin la borro antes) no hay evento al que volver.
        return eventQuestionService.deleteByAdmin(id)
                .map(eventId -> new ModelAndView("redirect:/eventos/" + eventId + "#preguntas"))
                .orElseGet(() -> new ModelAndView("redirect:/eventos"));
    }
}
