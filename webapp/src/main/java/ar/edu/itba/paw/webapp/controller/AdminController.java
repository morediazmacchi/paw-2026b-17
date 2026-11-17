package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.ReportStatusFilter;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.services.EventApprovalResult;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventRegistrants;
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
import ar.edu.itba.paw.webapp.util.PagerParams;
import ar.edu.itba.paw.webapp.util.ReportsListLink;
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

    // Panel unificado "Reportes y seguimiento": todos los reportes (cualquier estado), buscables por texto y
    // filtrables por pestaña (ver ReportStatusFilter). returnTo se arma una vez aca (con la busqueda, la
    // pestaña y la pagina que se estan viendo) y viaja en cada formulario de la pantalla, para que resolver
    // un reporte o abrir su seguimiento vuelva exactamente a esta misma vista (ver ReportsListLink).
    @RequestMapping(value = "/reportes", method = RequestMethod.GET)
    public ModelAndView reportes(@RequestParam(value = "q", required = false) final String q,
                                  @RequestParam(value = "filtro", required = false) final String filtro,
                                  @RequestParam(value = "page", defaultValue = "1") final int page,
                                  @RequestParam(value = "abierto", required = false) final String abierto) {
        final ReportStatusFilter effectiveFilter = ReportStatusFilter.fromParam(filtro);
        final ReportQueuePage queue = moderationService.searchQueue(q, effectiveFilter, page);
        final ModelAndView mav = new ModelAndView("admin/reportes");
        mav.addObject("reports", queue.getReports().getContent());
        // La pagina entera (con "mostrando X-Y"): la vista lee de ahi el rango y el total.
        mav.addObject("reportsPage", queue.getReports());
        mav.addObject("totalElements", queue.getReports().getTotalElements());
        mav.addObject("currentPage", queue.getReports().getPage());
        mav.addObject("totalPages", queue.getReports().getTotalPages());
        mav.addObject("counts", queue.getCounts());
        mav.addObject("q", q == null ? "" : q);
        mav.addObject("filtro", effectiveFilter.name());
        mav.addObject("abierto", abierto == null ? "" : abierto);
        mav.addObject("returnTo", ReportsListLink.of(q, effectiveFilter.name(), queue.getReports().getPage()));
        mav.addObject("pagerParams", PagerParams.of(q, "filtro", effectiveFilter.name()));
        return mav;
    }

    // El admin abre el seguimiento de un reporte: si todavia no lo habia abierto nadie, pasa de "Nuevo" a "En
    // revision" (ver ModerationService.markViewed()). No requiere canResolveReport: mirar el historial no es
    // una decision de moderacion (a diferencia de descartar/pedir cambios/dar de baja), asi que no hay
    // conflicto de intereses en verlo aunque el evento sea del propio admin.
    @RequestMapping(value = "/reportes/{id}/ver", method = RequestMethod.POST)
    public ModelAndView verReporte(@PathVariable final long id,
                                    @RequestParam(value = "returnTo", required = false) final String returnTo,
                                    @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        moderationService.markViewed(id, currentUser.getId());
        return new ModelAndView("redirect:" + ReportsListLink.withOpened(ReportsListLink.safeReturnTo(returnTo), id));
    }

    // Resolver un reporte (descartar, pedir cambios, dar de baja): un admin no puede si el evento reportado
    // es suyo (conflicto de intereses). Lo decide @PreAuthorize antes de entrar (ver
    // AdminSecurity.canResolveReport); la pantalla ya esconde los botones en ese caso.
    @PreAuthorize("@adminSecurity.canResolveReport(#id, authentication)")
    @RequestMapping(value = "/reportes/{id}/descartar", method = RequestMethod.POST)
    public ModelAndView descartar(@PathVariable final long id,
                                   @RequestParam(value = "returnTo", required = false) final String returnTo,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        final ReportResolutionResult result = moderationService.dismiss(id, currentUser.getId());
        // NOT_FOUND: otro admin ya lo resolvio mientras este lo tenia abierto.
        if (result != ReportResolutionResult.RESOLVED) {
            redirectAttributes.addFlashAttribute("adminReportError", result.name());
        }
        return new ModelAndView("redirect:" + ReportsListLink.safeReturnTo(returnTo));
    }

    @PreAuthorize("@adminSecurity.canResolveReport(#id, authentication)")
    @RequestMapping(value = "/reportes/{id}/dar-de-baja", method = RequestMethod.POST)
    public ModelAndView darDeBaja(@PathVariable final long id,
                                   @RequestParam(value = "suspenderOrganizador", required = false)
                                   final String suspenderOrganizador,
                                   @RequestParam(value = "returnTo", required = false) final String returnTo,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        final ReportResolutionResult result =
                moderationService.removeEvent(id, currentUser.getId(), suspenderOrganizador != null);
        if (result != ReportResolutionResult.RESOLVED) {
            redirectAttributes.addFlashAttribute("adminReportError", result.name());
        }
        return new ModelAndView("redirect:" + ReportsListLink.safeReturnTo(returnTo));
    }

    @PreAuthorize("@adminSecurity.canResolveReport(#id, authentication)")
    @RequestMapping(value = "/reportes/{id}/pedir-cambios", method = RequestMethod.POST)
    public ModelAndView pedirCambios(@PathVariable final long id, @Valid final RequestChangesForm form,
                                      final BindingResult errors,
                                      @RequestParam(value = "returnTo", required = false) final String returnTo,
                                      @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                      final RedirectAttributes redirectAttributes) {
        final String safeReturnTo = ReportsListLink.safeReturnTo(returnTo);
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("adminReportError", "INVALID");
            return new ModelAndView("redirect:" + safeReturnTo);
        }
        final ReportResolutionResult result = moderationService.requestChanges(id, currentUser.getId(), form.getMensaje());
        if (result != ReportResolutionResult.RESOLVED) {
            redirectAttributes.addFlashAttribute("adminReportError", result.name());
        }
        return new ModelAndView("redirect:" + safeReturnTo);
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

    @RequestMapping(value = "/usuarios", method = RequestMethod.GET)
    public ModelAndView usuarios(@RequestParam(value = "q", required = false) final String q,
                                  @RequestParam(value = "page", defaultValue = "1") final int page,
                                  @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<User> result = userService.findPage(q, page);

        final ModelAndView mav = new ModelAndView("admin/usuarios");
        mav.addObject("users", result.getContent());
        // La busqueda por mail: el texto para volver a mostrarlo (la pantalla lo escapa) y cuantos usuarios matchean.
        mav.addObject("q", q == null ? "" : q);
        // Las acciones de cada fila (suspender, promover...) vuelven aca, a la misma busqueda y pagina: ver UserListLink.
        mav.addObject("returnTo", UserListLink.of(q, result.getPage()));
        // Lo que el paginador repite en sus links para que cambiar de pagina conserve la busqueda.
        mav.addObject("pagerParams", PagerParams.of(q));
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

    @PreAuthorize("@adminSecurity.canReactivate(#id, authentication)")
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
    public ModelAndView eventos(@RequestParam(value = "page", defaultValue = "1") final int page,
                                @RequestParam(value = "q", required = false) final String query,
                                @RequestParam(value = "sort", required = false) final String sort) {
        final AdminEventSort effectiveSort = AdminEventSort.fromParam(sort);
        final PageResult<EventWithRegistrationStats> result =
                moderationService.findAllEventsPage(query, effectiveSort, page);
        final ModelAndView mav = new ModelAndView("admin/eventos");
        mav.addObject("events", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("q", query == null ? "" : query);
        mav.addObject("sort", effectiveSort.name());
        mav.addObject("sortOptions", AdminEventSort.values());
        // Lo que el paginador repite en sus links para que cambiar de pagina conserve la busqueda y el orden.
        mav.addObject("pagerParams", PagerParams.of(query, "sort", effectiveSort.name()));
        return mav;
    }

    // Inscriptos de un evento cualquiera, de solo lectura: a diferencia de
    // /perfil/eventos/{id}/inscriptos (del organizador, con boton de revocar), este panel es para
    // mirar y moderar, no para administrar inscripciones ajenas.
    @RequestMapping(value = "/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView inscriptos(@PathVariable final long id,
                                   @RequestParam(value = "page", defaultValue = "1") final int page) {
        final EventRegistrants registrants = registrationService.findRegistrants(id, page)
                .orElseThrow(() -> new EventNotFoundException(id));
        final PageResult<Registration> result = registrants.getRegistrations();
        final ModelAndView mav = new ModelAndView("admin/eventoInscriptos");
        mav.addObject("event", registrants.getEvent());
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
