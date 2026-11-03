package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.AccountCounts;
import ar.edu.itba.paw.services.AccountSummaryService;
import ar.edu.itba.paw.services.ApprovalResult;
import ar.edu.itba.paw.services.ChangePasswordResult;
import ar.edu.itba.paw.services.CreatorReputation;
import ar.edu.itba.paw.services.EventInvitationResult;
import ar.edu.itba.paw.services.EventInvitationService;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.EventWithRegistrationStats;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.InboxPage;
import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.services.MyRegistrationsView;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.PendingRegistrations;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewResult;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.services.UnregisterResult;
import ar.edu.itba.paw.services.UpdateProfileResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.ChangePasswordForm;
import ar.edu.itba.paw.webapp.form.InviteForm;
import ar.edu.itba.paw.webapp.form.ProfileForm;
import ar.edu.itba.paw.webapp.form.ReviewForm;
import ar.edu.itba.paw.webapp.util.FormErrors;
import ar.edu.itba.paw.webapp.util.Paging;
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

@Controller
public class ProfileController {

    private final UserService userService;
    private final RegistrationService registrationService;
    private final EventService eventService;
    private final FavoriteService favoriteService;
    private final ReviewService reviewService;
    private final EventInvitationService eventInvitationService;
    private final MessageSource messageSource;
    private final InboxService inboxService;
    private final EventChatService eventChatService;
    private final AccountSummaryService accountSummaryService;

    @Autowired
    public ProfileController(final UserService userService,
                              final RegistrationService registrationService, final EventService eventService,
                              final FavoriteService favoriteService, final ReviewService reviewService,
                              final EventInvitationService eventInvitationService,
                              final MessageSource messageSource, final InboxService inboxService,
                              final EventChatService eventChatService,
                              final AccountSummaryService accountSummaryService) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.eventService = eventService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.eventInvitationService = eventInvitationService;
        this.messageSource = messageSource;
        this.inboxService = inboxService;
        this.eventChatService = eventChatService;
        this.accountSummaryService = accountSummaryService;
    }

    private Map<String, String> fieldErrorsOf(final BindingResult errors) {
        return FormErrors.of(errors, messageSource);
    }

    // Los numeritos del menu lateral de la cuenta ("Mis inscripciones", favoritos, eventos, invitaciones):
    // los necesitan todas las paginas de la cuenta, asi que se piden una sola vez (una llamada al service)
    // y quedan en el model de cada handler como "accountCounts". Es un objeto y no numeros sueltos a
    // proposito: un @ModelAttribute tambien va al model de los "redirect:", y Spring agrega los valores
    // simples como query params (/perfil?registrationsCount=3); un objeto no. Ver UnreadInboxInterceptor.
    @ModelAttribute("accountCounts")
    public AccountCounts accountCounts(@ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        return currentUser == null ? null : accountSummaryService.countsFor(currentUser.getId(), currentUser.isAdmin());
    }

    @RequestMapping(value = "/perfil", method = RequestMethod.GET)
    public ModelAndView perfil(@RequestParam(value = "updated", required = false) final String updated,
                                @RequestParam(value = "passwordChanged", required = false) final String passwordChanged,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final ProfileForm profileForm = new ProfileForm();
        profileForm.setNombre(currentUser.getName());

        final ModelAndView mav = new ModelAndView("auth/perfil");
        mav.addObject("user", currentUser);
        mav.addObject("profileForm", profileForm);
        mav.addObject("changePasswordForm", new ChangePasswordForm());
        mav.addObject("profileUpdated", updated != null);
        mav.addObject("passwordChanged", passwordChanged != null);
        return mav;
    }

    @RequestMapping(value = "/perfil", method = RequestMethod.POST)
    public ModelAndView updateProfile(@Valid final ProfileForm form, final BindingResult errors,
                                       @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (errors.hasErrors()) {
            return profileWithErrors(form, errors, currentUser);
        }

        final UpdateProfileResult result = userService.updateProfile(currentUser.getId(), form.getNombre());
        if (result == UpdateProfileResult.INVALID_NAME) {
            errors.rejectValue("nombre", "Size.profileForm.nombre");
            return profileWithErrors(form, errors, currentUser);
        }
        return new ModelAndView("redirect:/perfil?updated");
    }

    private ModelAndView profileWithErrors(final ProfileForm form, final BindingResult errors, final User currentUser) {
        final ModelAndView mav = new ModelAndView("auth/perfil");
        mav.addObject("user", currentUser);
        mav.addObject("profileForm", form);
        mav.addObject("profileFieldErrors", fieldErrorsOf(errors));
        mav.addObject("changePasswordForm", new ChangePasswordForm());
        return mav;
    }

    @RequestMapping(value = "/perfil/contrasena", method = RequestMethod.POST)
    public ModelAndView changePassword(@Valid final ChangePasswordForm form, final BindingResult errors,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (!errors.hasErrors()) {
            final ChangePasswordResult result = userService.changePassword(
                    currentUser.getId(), form.getCurrentPassword(), form.getNewPassword());
            if (result == ChangePasswordResult.WRONG_CURRENT_PASSWORD) {
                errors.rejectValue("currentPassword", "wrongCurrentPassword");
            } else if (result == ChangePasswordResult.WEAK_PASSWORD) {
                errors.rejectValue("newPassword", "Size.changePasswordForm.newPassword");
            }
        }

        if (errors.hasErrors()) {
            final ProfileForm profileForm = new ProfileForm();
            profileForm.setNombre(currentUser.getName());

            final ModelAndView mav = new ModelAndView("auth/perfil");
            mav.addObject("user", currentUser);
            mav.addObject("profileForm", profileForm);
            mav.addObject("changePasswordForm", form);
            mav.addObject("passwordFieldErrors", fieldErrorsOf(errors));
            return mav;
        }

        return new ModelAndView("redirect:/perfil?passwordChanged");
    }

    @RequestMapping(value = "/perfil/inscripciones", method = RequestMethod.GET)
    public ModelAndView misInscripciones(@RequestParam(value = "proximasPage", required = false) final String upcomingPage,
                                         @RequestParam(value = "finalizadasPage", required = false) final String pastPage,
                                         @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        // Las proximas y las pasadas (con su evento y la reseña propia) son dos resultados paginados
        // independientes, cada uno con su indice (ver RegistrationService.findMyRegistrations()).
        final MyRegistrationsView result = registrationService.findMyRegistrations(
                currentUser.getId(), Paging.parsePage(upcomingPage), Paging.parsePage(pastPage));

        final ModelAndView mav = new ModelAndView("auth/inscripciones");
        mav.addObject("user", currentUser);
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

    @RequestMapping(value = "/perfil/inscripciones/{id}/resena", method = RequestMethod.POST)
    public ModelAndView leaveReview(@PathVariable final long id, @Valid final ReviewForm form,
                                     final BindingResult errors,
                                     @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                     final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("reviewError", true);
            return new ModelAndView("redirect:/perfil/inscripciones");
        }
        final ReviewResult result = reviewService.create(id, currentUser, form.getRating(), form.getComment());
        switch (result) {
            case CREATED:
                redirectAttributes.addFlashAttribute("reviewed", true);
                return new ModelAndView("redirect:/perfil/inscripciones");
            case ALREADY_REVIEWED:
                redirectAttributes.addFlashAttribute("alreadyReviewed", true);
                return new ModelAndView("redirect:/perfil/inscripciones");
            default:
                redirectAttributes.addFlashAttribute("reviewError", true);
                return new ModelAndView("redirect:/perfil/inscripciones");
        }
    }

    @RequestMapping(value = "/perfil/favoritos", method = RequestMethod.GET)
    public ModelAndView misFavoritos(@RequestParam(value = "page", required = false) final String page,
                                     @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<Event> result = favoriteService.findViewablePage(currentUser, Paging.parsePage(page));

        final ModelAndView mav = new ModelAndView("auth/favoritos");
        mav.addObject("user", currentUser);
        mav.addObject("favoriteEvents", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    @RequestMapping(value = "/perfil/invitaciones", method = RequestMethod.GET)
    public ModelAndView misInvitaciones(@RequestParam(value = "page", required = false) final String page,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<Event> result = eventInvitationService.findInvitedEventsPage(currentUser.getId(), Paging.parsePage(page));

        final ModelAndView mav = new ModelAndView("auth/invitaciones");
        mav.addObject("user", currentUser);
        mav.addObject("invitedEvents", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // Bandeja de mensajes. Al abrirla se marcan todos como leidos
    @RequestMapping(value = "/perfil/bandeja", method = RequestMethod.GET)
    public ModelAndView bandeja(@RequestParam(value = "page", required = false) final String page,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final InboxPage inbox = inboxService.findPageWithEvents(currentUser.getId(), Paging.parsePage(page));
        final PageResult<InboxMessage> result = inbox.getMessages();

        final ModelAndView mav = new ModelAndView("auth/bandeja");
        mav.addObject("user", currentUser);
        mav.addObject("messages", result.getContent());
        mav.addObject("hasUnread", inbox.isHasUnread());
        mav.addObject("chatGroups", eventChatService.findGroups(currentUser.getId()));
        mav.addObject("eventsById", inbox.getEventsById());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        return mav;
    }

    // "Marcar como leidas": solo los mensajes de la pagina que se estaba viendo (sus ids
    // vienen en el formulario). El GET de la bandeja no marca nada: solo lee.
    @RequestMapping(value = "/perfil/bandeja/marcar-leidas", method = RequestMethod.POST)
    public ModelAndView markInboxRead(@RequestParam(value = "ids", required = false) final List<Long> ids,
                                      @RequestParam(value = "page", required = false) final String page,
                                      @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        inboxService.markRead(currentUser.getId(), ids);
        return new ModelAndView("redirect:/perfil/bandeja?page=" + Paging.parsePage(page));
    }

    @RequestMapping(value = "/perfil/eventos", method = RequestMethod.GET)
    public ModelAndView misEventos(@RequestParam(value = "page", required = false) final String page,
                                   @RequestParam(value = "resenasPage", required = false) final String resenasPage,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<EventWithRegistrationStats> result =
                eventService.findMyEventsPage(currentUser.getId(), Paging.parsePage(page));
        final CreatorReputation reputation =
                reviewService.findCreatorReputation(currentUser.getId(), Paging.parsePage(resenasPage));

        final ModelAndView mav = new ModelAndView("auth/eventos");
        mav.addObject("user", currentUser);
        // Cada evento con sus conteos de inscripciones (pendientes y confirmados, ver EventWithRegistrationStats).
        mav.addObject("createdEvents", result.getContent());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        // Popup "Ver mis reseñas": lo mismo que EventController.addCreatorReputation (total y
        // promedio en la base, una pagina de reseñas con ?resenasPage=N).
        mav.addObject("creatorAverageRating", reputation.getAverageRating());
        mav.addObject("creatorReviewCount", reputation.getReviewCount());
        if (reputation.hasReviews()) {
            mav.addObject("myCreatorReviews", reputation.getReviews());
            mav.addObject("reviewEventsById", reputation.getEventsById());
            mav.addObject("creatorReviewsPage", reputation.getPage());
            mav.addObject("creatorReviewsTotalPages", reputation.getTotalPages());
        }
        return mav;
    }

    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView pendingRegistrations(@PathVariable final long id,
                                              @RequestParam(value = "approved", required = false) final String approvedParam,
                                              @RequestParam(value = "rejected", required = false) final String rejectedParam,
                                              @RequestParam(value = "full", required = false) final String fullParam,
                                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);

        final ModelAndView mav = new ModelAndView("auth/inscriptos");
        mav.addObject("user", currentUser);
        mav.addObject("event", event);
        final PendingRegistrations pending = registrationService.findPendingSummary(id);
        mav.addObject("pendingRegistrations", pending.getRegistrations());
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
                                    @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        return inviteFormView(event, currentUser, new InviteForm(), null, sentParam != null, alreadyInvitedParam != null);
    }

    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.POST)
    public ModelAndView invite(@PathVariable final long id, @Valid final InviteForm form, final BindingResult errors,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        if (errors.hasErrors()) {
            return inviteFormView(event, currentUser, form, errors, false, false);
        }
        // Que el evento sea privado, no este cancelado ni haya pasado lo valida el service.
        final EventInvitationResult result = eventInvitationService.invite(id, form.getEmail());
        if (result == EventInvitationResult.NOT_ALLOWED) {
            return new ModelAndView("redirect:/perfil/eventos");
        }
        if (result == EventInvitationResult.INVALID_EMAIL) {
            errors.rejectValue("email", "Size.inviteForm.email");
            return inviteFormView(event, currentUser, form, errors, false, false);
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
            case NOT_ALLOWED, INVALID_EMAIL -> throw new IllegalStateException(result + " ya se filtro antes de llegar aca");
        };
    }

    private ModelAndView inviteFormView(final Event event, final User currentUser, final InviteForm form,
                                         final BindingResult errors, final boolean sent, final boolean alreadyInvited) {
        final List<EventInvitation> invitations = eventInvitationService.findByEvent(event.getId());
        final ModelAndView mav = new ModelAndView("auth/invitar");
        mav.addObject("user", currentUser);
        mav.addObject("event", event);
        mav.addObject("inviteForm", form);
        if (errors != null) {
            mav.addObject("fieldErrors", fieldErrorsOf(errors));
        }
        mav.addObject("invitations", invitations);
        mav.addObject("sent", sent);
        mav.addObject("alreadyInvited", alreadyInvited);
        return mav;
    }
}
