package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import ar.edu.itba.paw.services.ApprovalResult;
import ar.edu.itba.paw.services.ChangePasswordResult;
import ar.edu.itba.paw.services.CreatorReputation;
import ar.edu.itba.paw.services.EventInvitationResult;
import ar.edu.itba.paw.services.EventInvitationService;
import ar.edu.itba.paw.services.EventWithRegistrationStats;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.FollowService;
import ar.edu.itba.paw.services.InboxPage;
import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.services.InviteView;
import ar.edu.itba.paw.services.MyAccountService;
import ar.edu.itba.paw.services.MyEventsView;
import ar.edu.itba.paw.services.MyInboxView;
import ar.edu.itba.paw.services.MyRegistrationsView;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.PendingRegistrations;
import ar.edu.itba.paw.services.PendingRequestsView;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.SavedSearchService;
import ar.edu.itba.paw.services.SavedSearchWithCategoryNames;
import ar.edu.itba.paw.services.UnregisterResult;
import ar.edu.itba.paw.services.UpdateProfileResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.ChangePasswordForm;
import ar.edu.itba.paw.webapp.form.InviteForm;
import ar.edu.itba.paw.webapp.form.ProfileForm;
import ar.edu.itba.paw.webapp.util.FormErrors;
import ar.edu.itba.paw.webapp.util.SavedSearchView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class ProfileController {

    private final UserService userService;
    private final RegistrationService registrationService;
    private final FavoriteService favoriteService;
    private final EventInvitationService eventInvitationService;
    private final MessageSource messageSource;
    private final InboxService inboxService;
    private final SavedSearchService savedSearchService;
    private final FollowService followService;
    private final MyAccountService myAccountService;

    @Autowired
    public ProfileController(final UserService userService,
                              final RegistrationService registrationService,
                              final FavoriteService favoriteService,
                              final EventInvitationService eventInvitationService,
                              final MessageSource messageSource, final InboxService inboxService,
                              final SavedSearchService savedSearchService,
                              final FollowService followService,
                              final MyAccountService myAccountService) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.eventInvitationService = eventInvitationService;
        this.messageSource = messageSource;
        this.inboxService = inboxService;
        this.savedSearchService = savedSearchService;
        this.followService = followService;
        this.myAccountService = myAccountService;
    }

    private Map<String, String> fieldErrorsOf(final BindingResult errors) {
        return FormErrors.of(errors, messageSource);
    }

    // La barra lateral de la cuenta (accountCounts y user) la pone AccountSidebarInterceptor en las paginas de
    // /perfil/**: aca no se arma. La pantalla de perfil si necesita al usuario para su formulario, asi que lo pide
    // ella misma (ver accountOf()).
    private User accountOf(final User currentUser) {
        return userService.findById(currentUser.getId())
                .orElseThrow(() -> new UserNotFoundException(currentUser.getId()));
    }

    @RequestMapping(value = "/perfil", method = RequestMethod.GET)
    public ModelAndView perfil(@RequestParam(value = "updated", required = false) final String updated,
                                @RequestParam(value = "passwordChanged", required = false) final String passwordChanged,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final User user = accountOf(currentUser);
        final ModelAndView mav = profileView(user, nameForm(user), new ChangePasswordForm());
        mav.addObject("profileUpdated", updated != null);
        mav.addObject("passwordChanged", passwordChanged != null);
        return mav;
    }

    @RequestMapping(value = "/perfil", method = RequestMethod.POST)
    public ModelAndView updateProfile(@Valid final ProfileForm form, final BindingResult errors,
                                       @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (!errors.hasErrors()) {
            final UpdateProfileResult result = userService.updateProfile(currentUser.getId(), form.getNombre());
            if (result != UpdateProfileResult.INVALID_NAME) {
                return new ModelAndView("redirect:/perfil?updated");
            }
            errors.rejectValue("nombre", "Size.profileForm.nombre");
        }
        final ModelAndView mav = profileView(accountOf(currentUser), form, new ChangePasswordForm());
        mav.addObject("profileFieldErrors", fieldErrorsOf(errors));
        return mav;
    }

    @RequestMapping(value = "/perfil/contrasena", method = RequestMethod.POST)
    public ModelAndView changePassword(@Valid final ChangePasswordForm form, final BindingResult errors,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (!errors.hasErrors()) {
            final ChangePasswordResult result = userService.changePassword(
                    currentUser.getId(), form.getCurrentPassword(), form.getNewPassword());
            if (result == ChangePasswordResult.OK) {
                return new ModelAndView("redirect:/perfil?passwordChanged");
            }
            if (result == ChangePasswordResult.WRONG_CURRENT_PASSWORD) {
                errors.rejectValue("currentPassword", "wrongCurrentPassword");
            } else {
                errors.rejectValue("newPassword", "Size.changePasswordForm.newPassword");
            }
        }
        final User user = accountOf(currentUser);
        final ModelAndView mav = profileView(user, nameForm(user), form);
        mav.addObject("passwordFieldErrors", fieldErrorsOf(errors));
        return mav;
    }

    // La pantalla de "Mi perfil" con sus dos formularios (nombre y contraseña): la misma para mostrarla, y para
    // volver a mostrarla con errores de cualquiera de los dos.
    private ModelAndView profileView(final User user, final ProfileForm profileForm,
                                     final ChangePasswordForm changePasswordForm) {
        final ModelAndView mav = new ModelAndView("auth/perfil");
        mav.addObject("user", user);
        mav.addObject("followersCount", followService.countFollowers(user.getId()));
        mav.addObject("profileForm", profileForm);
        mav.addObject("changePasswordForm", changePasswordForm);
        return mav;
    }

    private static ProfileForm nameForm(final User user) {
        final ProfileForm form = new ProfileForm();
        form.setNombre(user.getName());
        return form;
    }

    @RequestMapping(value = "/perfil/inscripciones", method = RequestMethod.GET)
    public ModelAndView misInscripciones(@RequestParam(value = "proximasPage", defaultValue = "1") final int upcomingPage,
                                         @RequestParam(value = "finalizadasPage", defaultValue = "1") final int pastPage,
                                         @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        // Las proximas y las pasadas (con su evento y la reseña propia) son dos resultados paginados
        // independientes, cada uno con su indice (ver RegistrationService.findMyRegistrations()).
        final MyRegistrationsView result = registrationService.findMyRegistrations(
                currentUser.getId(), upcomingPage, pastPage);

        final ModelAndView mav = new ModelAndView("auth/inscripciones");
        mav.addObject("upcomingRegistrations", result.getUpcoming());
        mav.addObject("pastRegistrations", result.getPast());
        return mav;
    }

    // El cartel "Cancelaste tu inscripcion" va como flash attribute y no como
    // ?cancelled en la URL: Spring lo guarda en la sesion al redirigir, lo pone en
    // el model de la pagina siguiente ("cancelled") y lo borra. Con la URL, el
    // cartel volvia a salir cada vez que se recargaba o se volvia a esa pestania,
    // aunque despues hubiera pasado otra cosa (por ejemplo, que el organizador
    // cancelara el evento).
    @PreAuthorize("@eventSecurity.isRegistrant(#id, authentication)")
    @RequestMapping(value = "/perfil/inscripciones/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelarInscripcion(@PathVariable final long id,
                                             final RedirectAttributes redirectAttributes) {
        final UnregisterResult result = registrationService.cancel(id);
        redirectAttributes.addFlashAttribute("cancelled", wasCancelled(result));
        return new ModelAndView("redirect:/perfil/inscripciones");
    }

    private static boolean wasCancelled(final UnregisterResult result) {
        return switch (result) {
            case CANCELLED, CANCELLED_WAITLISTED, CANCELLED_PENDING -> true;
            case NOT_FOUND, REJECTED, FINISHED, EVENT_CANCELLED -> false;
        };
    }

    @RequestMapping(value = "/perfil/favoritos", method = RequestMethod.GET)
    public ModelAndView misFavoritos(@RequestParam(value = "page", defaultValue = "1") final int page,
                                     @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<Event> result = favoriteService.findViewablePage(currentUser.getId(), currentUser.isAdmin(), page);

        final ModelAndView mav = new ModelAndView("auth/favoritos");
        mav.addObject("favoriteEvents", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // "Mis listas": las busquedas guardadas de /eventos, una debajo de la otra, cada una con "Ver eventos" y
    // "Eliminar lista". Paginadas: no hay maximo de listas por persona.
    @RequestMapping(value = "/perfil/listas", method = RequestMethod.GET)
    public ModelAndView misListas(@RequestParam(value = "page", defaultValue = "1") final int page,
                                  @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<SavedSearchWithCategoryNames> result =
                myAccountService.findSavedSearches(currentUser.getId(), page);

        final ModelAndView mav = new ModelAndView("auth/listas");
        mav.addObject("savedSearches", result.getContent().stream()
                .map(SavedSearchView::of)
                .collect(Collectors.toList()));
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // Vuelve a la misma pagina de "Mis listas" (el service la ajusta si ya no existe, por ejemplo al borrar la
    // unica lista de la ultima pagina). De quien es la lista lo decide @PreAuthorize antes de entrar (ver
    // SavedSearchSecurity). El resultado viaja como flash: true = se borro, false = otra pestana la borro justo antes.
    @PreAuthorize("@savedSearchSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/listas/{id}/eliminar", method = RequestMethod.POST)
    public ModelAndView eliminarLista(@PathVariable final long id,
                                      @RequestParam(value = "page", defaultValue = "1") final int page,
                                      final RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("listDeleted", savedSearchService.delete(id));
        return new ModelAndView("redirect:/perfil/listas?page=" + page);
    }

    // A donde lleva el boton "Desuscribirme de esta lista" del mail. Pide sesion como todo /perfil (despues de
    // loguearse vuelve aca) y muestra los filtros de la lista para confirmar; no borra nada hasta el POST de abajo.
    // De quien es la lista lo decide @PreAuthorize antes de entrar (ver SavedSearchSecurity): una que no existe o es de
    // otra persona da el mismo 404. Si se borra justo entre la regla y esta lectura, vuelve a "Mis listas" avisando.
    @PreAuthorize("@savedSearchSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/listas/{id}/desuscribirse", method = RequestMethod.GET)
    public ModelAndView confirmarDesuscripcion(@PathVariable final long id,
                                               final RedirectAttributes redirectAttributes) {
        final Optional<SavedSearchWithCategoryNames> search = myAccountService.findSavedSearch(id);
        if (!search.isPresent()) {
            redirectAttributes.addFlashAttribute("listDeleted", false);
            return new ModelAndView("redirect:/perfil/listas");
        }
        final ModelAndView mav = new ModelAndView("auth/desuscribirLista");
        mav.addObject("savedSearch", SavedSearchView.of(search.get()));
        return mav;
    }

    @PreAuthorize("@savedSearchSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/listas/{id}/desuscribirse", method = RequestMethod.POST)
    public ModelAndView desuscribirse(@PathVariable final long id,
                                      final RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("listDeleted", savedSearchService.delete(id));
        return new ModelAndView("redirect:/perfil/listas");
    }

    @RequestMapping(value = "/perfil/invitaciones", method = RequestMethod.GET)
    public ModelAndView misInvitaciones(@RequestParam(value = "page", defaultValue = "1") final int page,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<Event> result = eventInvitationService.findInvitedEventsPage(currentUser.getId(), page);

        final ModelAndView mav = new ModelAndView("auth/invitaciones");
        mav.addObject("invitedEvents", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // Bandeja de mensajes. Abrirla no marca nada como leido (eso es el POST de marcar-leidas, abajo). Los circulos de chat y los
    // mensajes son dos paginados independientes (page / gruposPage), cada uno con su indice: ver
    // pager.tag (pageParam/otherParam/otherPage), mismo patron que "Mis inscripciones".
    @RequestMapping(value = "/perfil/bandeja", method = RequestMethod.GET)
    public ModelAndView bandeja(@RequestParam(value = "page", defaultValue = "1") final int page,
                                @RequestParam(value = "gruposPage", defaultValue = "1") final int groupsPage,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final MyInboxView view = myAccountService.findInbox(currentUser.getId(), page,
                groupsPage);
        final InboxPage inbox = view.getInbox();
        final PageResult<InboxMessage> result = inbox.getMessages();

        final ModelAndView mav = new ModelAndView("auth/bandeja");
        mav.addObject("messages", result.getContent());
        mav.addObject("hasUnread", inbox.isHasUnread());
        // Los circulos de los grupos y los mensajes son dos listas paginadas independientes, cada una con su
        // parametro (page / gruposPage); el JSP repite el de la otra en los links de cada paginador.
        final PageResult<ChatGroup> groups = view.getChatGroups();
        mav.addObject("chatGroups", groups.getContent());
        mav.addObject("groupsPage", groups.getPage());
        mav.addObject("groupsTotalPages", groups.getTotalPages());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // "Marcar como leidas": solo los mensajes de la pagina que se estaba viendo (sus ids
    // vienen en el formulario). El GET de la bandeja no marca nada: solo lee. gruposPage viaja de
    // vuelta en el redirect para no resetear el paginador de grupos al marcar mensajes leidos.
    @RequestMapping(value = "/perfil/bandeja/marcar-leidas", method = RequestMethod.POST)
    public ModelAndView markInboxRead(@RequestParam(value = "ids", required = false) final List<Long> ids,
                                      @RequestParam(value = "page", defaultValue = "1") final int page,
                                      @RequestParam(value = "gruposPage", defaultValue = "1") final int groupsPage,
                                      @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        inboxService.markRead(currentUser.getId(), ids);
        return new ModelAndView("redirect:/perfil/bandeja?page=" + page
                + "&gruposPage=" + groupsPage);
    }

    @RequestMapping(value = "/perfil/eventos", method = RequestMethod.GET)
    public ModelAndView misEventos(@RequestParam(value = "page", defaultValue = "1") final int page,
                                   @RequestParam(value = "resenasPage", defaultValue = "1") final int resenasPage,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final MyEventsView view = myAccountService.findMyEvents(currentUser.getId(), page,
                resenasPage);
        final PageResult<EventWithRegistrationStats> result = view.getEvents();
        final CreatorReputation reputation = view.getReputation();

        final ModelAndView mav = new ModelAndView("auth/eventos");
        // Cada evento con sus conteos de inscripciones (pendientes y confirmados, ver EventWithRegistrationStats).
        mav.addObject("createdEvents", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        // Popup "Ver mis reseñas": lo mismo que EventController.addCreatorReputation (total y
        // promedio en la base, una pagina de reseñas con ?resenasPage=N).
        // Sin reseñas todo esto viene vacio o en 0 (la vista mira creatorReviewCount): no se decide aca.
        mav.addObject("creatorAverageRating", reputation.getAverageRating());
        mav.addObject("creatorReviewCount", reputation.getReviewCount());
        mav.addObject("myCreatorReviews", reputation.getReviews());
        mav.addObject("creatorReviewsPage", reputation.getPage());
        mav.addObject("creatorReviewsTotalPages", reputation.getTotalPages());
        return mav;
    }

    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView pendingRegistrations(@PathVariable final long id,
                                              @RequestParam(value = "page", defaultValue = "1") final int page,
                                              @RequestParam(value = "approved", required = false) final String approvedParam,
                                              @RequestParam(value = "rejected", required = false) final String rejectedParam,
                                              @RequestParam(value = "full", required = false) final String fullParam) {
        final PendingRequestsView view = myAccountService.findPendingRequests(id, page)
                .orElseThrow(() -> new EventNotFoundException(id));
        final PendingRegistrations pending = view.getPending();

        final ModelAndView mav = new ModelAndView("auth/inscriptos");
        mav.addObject("event", view.getEvent());
        mav.addObject("pendingRegistrations", pending.getRegistrations());
        mav.addObject("currentPage", pending.getPage());
        mav.addObject("totalPages", pending.getTotalPages());
        mav.addObject("approved", approvedParam != null);
        mav.addObject("rejected", rejectedParam != null);
        mav.addObject("fullNotice", fullParam != null);
        // Sin lugar libres el organizador ya no puede aceptar a nadie mas (ver
        // RegistrationService.approve()): la pantalla lo dice y deshabilita "Aceptar".
        mav.addObject("availableSpots", pending.getAvailableSpots());
        mav.addObject("noSpotsLeft", pending.isFull());
        return mav;
    }

    @PreAuthorize("@eventSecurity.isOwnerOfRegistrationEvent(#id, #registrationId, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/aceptar", method = RequestMethod.POST)
    public ModelAndView approveRegistration(@PathVariable final long id, @PathVariable final long registrationId) {
        final ApprovalResult result = registrationService.approve(registrationId);
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/inscriptos" + approveParam(result.getStatus()));
    }

    // Switch expression sin default (mismo patron que wasCancelled() y que
    // unregisterParam() en EventRegistrationController): si se agrega un valor
    // a ApprovalResult.Status, esto deja de compilar hasta decidir que mostrar.
    private static String approveParam(final ApprovalResult.Status status) {
        return switch (status) {
            case APPROVED -> "?approved";
            case FULL -> "?full";
            case NOT_FOUND -> "";
        };
    }

    @PreAuthorize("@eventSecurity.isOwnerOfRegistrationEvent(#id, #registrationId, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/rechazar", method = RequestMethod.POST)
    public ModelAndView rejectRegistration(@PathVariable final long id, @PathVariable final long registrationId) {
        final boolean rejected = registrationService.reject(registrationId);
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/inscriptos" + (rejected ? "?rejected" : ""));
    }

    // Volver de "Ver inscriptos" (donde esta el boton Revocar) a esa misma lista.
    @PreAuthorize("@eventSecurity.isOwnerOfRegistrationEvent(#id, #registrationId, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/revocar", method = RequestMethod.POST)
    public ModelAndView revokeRegistration(@PathVariable final long id, @PathVariable final long registrationId) {
        final boolean revoked = registrationService.revoke(registrationId);
        return new ModelAndView("redirect:/eventos/" + id + "/inscriptos" + (revoked ? "?revoked" : ""));
    }

    // Invitar: mismo 404 que pendingRegistrations si no sos el creador (lo decide @PreAuthorize, ver
    // EventSecurity.isOwner). Que el evento sea privado NO es de acceso: en uno publico se muestra un
    // cartel en esta misma pantalla (ver EventInvitationResult.EVENT_PUBLIC).
    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.GET)
    public ModelAndView inviteForm(@PathVariable final long id,
                                    @RequestParam(value = "sent", required = false) final String sentParam,
                                    @RequestParam(value = "alreadyInvited", required = false) final String alreadyInvitedParam,
                                    @RequestParam(value = "page", defaultValue = "1") final int page) {
        return inviteFormView(id, new InviteForm(), null, sentParam != null,
                alreadyInvitedParam != null, page);
    }

    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.POST)
    public ModelAndView invite(@PathVariable final long id, @Valid final InviteForm form, final BindingResult errors) {
        if (errors.hasErrors()) {
            return inviteFormView(id, form, errors, false, false, 1);
        }
        // Que el evento sea privado, no este cancelado ni haya pasado lo valida el service.
        final EventInvitationResult result = eventInvitationService.invite(id, form.getEmail());
        if (result == EventInvitationResult.NOT_ALLOWED) {
            return new ModelAndView("redirect:/perfil/eventos");
        }
        if (result == EventInvitationResult.INVALID_EMAIL) {
            errors.rejectValue("email", "Size.inviteForm.email");
            return inviteFormView(id, form, errors, false, false, 1);
        }
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/invitar?" + inviteParam(result));
    }

    // Switch expression sin default: si se agrega un valor a EventInvitationResult,
    // esto deja de compilar hasta decidir que mostrar. NOT_ALLOWED ya se filtro
    // arriba (va a una pagina distinta): llegar aca con ese valor séria un bug.
    private static String inviteParam(final EventInvitationResult result) {
        return switch (result) {
            case SENT -> "sent";
            case ALREADY_INVITED -> "alreadyInvited";
            case FULL -> "full";
            case SELF_INVITE -> "selfInvite";
            case EVENT_PUBLIC -> "eventPublic";
            case NOT_APPROVED -> "notApproved";
            case NOT_ALLOWED, INVALID_EMAIL -> throw new IllegalStateException(result + " ya se filtro antes de llegar aca");
        };
    }

    private ModelAndView inviteFormView(final long eventId, final InviteForm form,
                                         final BindingResult errors, final boolean sent, final boolean alreadyInvited,
                                         final int page) {
        final InviteView view = myAccountService.findInviteScreen(eventId, page)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        final PageResult<EventInvitation> invitations = view.getInvitations();
        final ModelAndView mav = new ModelAndView("auth/invitar");
        mav.addObject("event", view.getEvent());
        mav.addObject("inviteForm", form);
        if (errors != null) {
            mav.addObject("fieldErrors", fieldErrorsOf(errors));
        }
        mav.addObject("invitations", invitations.getContent());
        mav.addObject("currentPage", invitations.getPage());
        mav.addObject("totalPages", invitations.getTotalPages());
        mav.addObject("sent", sent);
        mav.addObject("alreadyInvited", alreadyInvited);
        return mav;
    }
}
