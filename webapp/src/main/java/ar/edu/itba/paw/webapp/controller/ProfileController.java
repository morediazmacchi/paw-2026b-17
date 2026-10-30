package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.ApprovalResult;
import ar.edu.itba.paw.services.EventInvitationResult;
import ar.edu.itba.paw.services.EventInvitationService;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewResult;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.services.UnregisterResult;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Autowired
    public ProfileController(final UserService userService,
                              final RegistrationService registrationService, final EventService eventService,
                              final FavoriteService favoriteService, final ReviewService reviewService,
                              final EventInvitationService eventInvitationService,
                              final MessageSource messageSource, final InboxService inboxService,
                              final EventChatService eventChatService) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.eventService = eventService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.eventInvitationService = eventInvitationService;
        this.messageSource = messageSource;
        this.inboxService = inboxService;
        this.eventChatService = eventChatService;
    }

    private Map<String, String> fieldErrorsOf(final BindingResult errors) {
        return FormErrors.of(errors, messageSource);
    }

    // Para el badge de "Mis inscripciones"
    private int registrationsCountFor(final User user) {
        return (int) registrationService.countByUserId(user.getId());
    }

    // el badge de "Favoritos" (solo los que todavia puede ver: ver FavoriteService.countViewable)
    private int favoritesCountFor(final User user) {
        return (int) favoriteService.countViewable(user);
    }

    // Mismo motivo que registrationsCountFor: el badge de "Mis eventos" en el
    // sidebar se pide en todas las paginas de la cuenta.
    private int eventsCountFor(final User user) {
        return (int) eventService.countByCreatorId(user.getId());
    }

    // Mismo motivo que registrationsCountFor: el badge de "Mis invitaciones"
    // en el sidebar se pide en todas las paginas de la cuenta.
    private int invitationsCountFor(final User user) {
        return (int) eventInvitationService.countByUser(user.getId());
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
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        return mav;
    }

    @RequestMapping(value = "/perfil", method = RequestMethod.POST)
    public ModelAndView updateProfile(@Valid final ProfileForm form, final BindingResult errors,
                                       @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (errors.hasErrors()) {
            final ModelAndView mav = new ModelAndView("auth/perfil");
            mav.addObject("user", currentUser);
            mav.addObject("profileForm", form);
            mav.addObject("profileFieldErrors", fieldErrorsOf(errors));
            mav.addObject("changePasswordForm", new ChangePasswordForm());
            mav.addObject("registrationsCount", registrationsCountFor(currentUser));
            mav.addObject("favoritesCount", favoritesCountFor(currentUser));
            mav.addObject("eventsCount", eventsCountFor(currentUser));
            mav.addObject("invitationsCount", invitationsCountFor(currentUser));
            return mav;
        }

        userService.updateProfile(currentUser.getId(), form.getNombre());
        return new ModelAndView("redirect:/perfil?updated");
    }

    @RequestMapping(value = "/perfil/contrasena", method = RequestMethod.POST)
    public ModelAndView changePassword(@Valid final ChangePasswordForm form, final BindingResult errors,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (!errors.hasErrors()) {
            final boolean ok = userService.changePassword(currentUser.getId(), form.getCurrentPassword(), form.getNewPassword());
            if (!ok) {
                errors.rejectValue("currentPassword", "wrongCurrentPassword");
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
            mav.addObject("registrationsCount", registrationsCountFor(currentUser));
            mav.addObject("favoritesCount", favoritesCountFor(currentUser));
            mav.addObject("eventsCount", eventsCountFor(currentUser));
            mav.addObject("invitationsCount", invitationsCountFor(currentUser));
            return mav;
        }

        return new ModelAndView("redirect:/perfil?passwordChanged");
    }

    @RequestMapping(value = "/perfil/inscripciones", method = RequestMethod.GET)
    public ModelAndView misInscripciones(@RequestParam(value = "page", required = false) final String page,
                                         @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<Registration> result = registrationService.findPageByUserId(currentUser.getId(), Paging.parsePage(page));
        final List<Registration> registrations = result.getContent();

        final List<Long> eventIds = registrations.stream()
                .map(Registration::getEventId).collect(Collectors.toList());
        final Map<Long, Event> eventsById = eventService.findByIds(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));

        final List<Registration> upcoming = new ArrayList<>();
        final List<Registration> past = new ArrayList<>();
        for (final Registration registration : registrations) {
            final Event event = eventsById.get(registration.getEventId());
            if (event == null) {
                continue;
            }
            (event.isFinished() ? past : upcoming).add(registration);
        }

        // Reseña propia de cada evento finalizado (si ya la mando)
        final List<Long> pastEventIds = past.stream()
                .map(Registration::getEventId).collect(Collectors.toList());
        final Map<Long, Review> myReviewsByEventId = reviewService.findByEventsAndUser(pastEventIds, currentUser.getId())
                .stream().collect(Collectors.toMap(Review::getEventId, review -> review));

        final ModelAndView mav = new ModelAndView("auth/inscripciones");
        mav.addObject("user", currentUser);
        mav.addObject("upcomingRegistrations", upcoming);
        mav.addObject("pastRegistrations", past);
        mav.addObject("eventsById", eventsById);
        mav.addObject("myReviewsByEventId", myReviewsByEventId);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("registrationsCount", (int) result.getTotalElements());
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
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
            case NOT_FOUND, REJECTED, FINISHED -> false;
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
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", (int) result.getTotalElements());
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        return mav;
    }

    @RequestMapping(value = "/perfil/invitaciones", method = RequestMethod.GET)
    public ModelAndView misInvitaciones(@RequestParam(value = "page", required = false) final String page,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<EventInvitation> result = eventInvitationService.findPageByUser(currentUser.getId(), Paging.parsePage(page));
        final List<Event> invitedEvents = new ArrayList<>();
        for (final EventInvitation invitation : result.getContent()) {
            eventService.findById(invitation.getEventId()).ifPresent(invitedEvents::add);
        }

        final ModelAndView mav = new ModelAndView("auth/invitaciones");
        mav.addObject("user", currentUser);
        mav.addObject("invitedEvents", invitedEvents);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", (int) result.getTotalElements());
        return mav;
    }

    // Bandeja de mensajes. Al abrirla se marcan todos como leidos
    @RequestMapping(value = "/perfil/bandeja", method = RequestMethod.GET)
    public ModelAndView bandeja(@RequestParam(value = "page", required = false) final String page,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<InboxMessage> result = inboxService.findPage(currentUser.getId(), Paging.parsePage(page));
        final List<Long> eventIds = result.getContent().stream()
                .map(InboxMessage::getEventId).filter(Objects::nonNull).collect(Collectors.toList());
        final Map<Long, Event> eventsById = eventService.findByIds(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));

        final ModelAndView mav = new ModelAndView("auth/bandeja");
        mav.addObject("user", currentUser);
        mav.addObject("messages", result.getContent());
        mav.addObject("chatGroups", eventChatService.findGroups(currentUser.getId()));
        mav.addObject("eventsById", eventsById);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
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
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final PageResult<Event> result = eventService.findPageByCreatorId(currentUser.getId(), Paging.parsePage(page));
        final List<Event> createdEvents = result.getContent();
        final List<Review> myReviews = reviewService.findByCreatorId(currentUser.getId());

        final ModelAndView mav = new ModelAndView("auth/eventos");
        mav.addObject("user", currentUser);
        mav.addObject("createdEvents", createdEvents);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", (int) result.getTotalElements());
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        mav.addObject("creatorAverageRating", reviewService.findCreatorAverageRating(currentUser.getId()));
        mav.addObject("creatorReviewCount", myReviews.size());
        if (!myReviews.isEmpty()) {
            // Para el popup "Ver mis reseñas": mismo patron que
            // EventController.addCreatorReputation, pero disparado desde ac.
            mav.addObject("myCreatorReviews", myReviews);
            // Una sola consulta para todos los eventos reseñados (findByIds()),
            // en vez de un findById() por cada reseña.
            final List<Long> eventIds = myReviews.stream().map(Review::getEventId).distinct().collect(Collectors.toList());
            final Map<Long, Event> reviewEventsById = eventService.findByIds(eventIds).stream()
                    .collect(Collectors.toMap(Event::getId, e -> e));
            mav.addObject("reviewEventsById", reviewEventsById);
        }
        // Badge "N pendientes" por evento en eventRow.tag: solo tiene sentido
        // para eventos con requiresApproval, no vale la pena consultar el resto.
        final Map<Long, Long> pendingCountsByEventId = new HashMap<>();
        for (final Event event : createdEvents) {
            if (event.isRequiresApproval()) {
                pendingCountsByEventId.put(event.getId(),
                        registrationService.countPendingByEvent(event.getId(), currentUser.getId()));
            }
        }
        mav.addObject("pendingCountsByEventId", pendingCountsByEventId);

        // Para el texto del modal de "Cancelar evento" (cuantos inscriptos
        // confirmados se van a enterar por mail): solo tiene sentido calcularlo
        // para eventos activos, uno ya cancelado no muestra el boton.
        final Map<Long, Long> confirmedCountsByEventId = new HashMap<>();
        for (final Event event : createdEvents) {
            if (!event.isCancelled()) {
                confirmedCountsByEventId.put(event.getId(), registrationService.countConfirmed(event.getId()));
            }
        }
        mav.addObject("confirmedCountsByEventId", confirmedCountsByEventId);
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
        mav.addObject("pendingRegistrations", registrationService.findPendingByEvent(id, currentUser.getId()));
        mav.addObject("approved", approvedParam != null);
        mav.addObject("rejected", rejectedParam != null);
        mav.addObject("fullNotice", fullParam != null);
        // Sin lugar libres el organizador ya no puede aceptar a nadie mas (ver
        // RegistrationService.approve()): la pantalla lo dice y deshabilita "Aceptar".
        final Integer availableSpots = registrationService.availableSpots(id);
        mav.addObject("availableSpots", availableSpots);
        mav.addObject("noSpotsLeft", registrationService.isFull(id));
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        return mav;
    }

    @PreAuthorize("@eventSecurity.isOwnerOfRegistrationEvent(#id, #registrationId, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/aceptar", method = RequestMethod.POST)
    public ModelAndView approveRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                             @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final ApprovalResult result = registrationService.approve(registrationId, currentUser.getId());
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
    public ModelAndView rejectRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                            @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final boolean rejected = registrationService.reject(registrationId, currentUser.getId());
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/inscriptos" + (rejected ? "?rejected" : ""));
    }

    // Volver de "Ver inscriptos" (donde esta el boton Revocar) a esa misma lista.
    @PreAuthorize("@eventSecurity.isOwnerOfRegistrationEvent(#id, #registrationId, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/revocar", method = RequestMethod.POST)
    public ModelAndView revokeRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                            @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final boolean revoked = registrationService.revoke(registrationId, currentUser.getId());
        return new ModelAndView("redirect:/eventos/" + id + "/inscriptos" + (revoked ? "?revoked" : ""));
    }

    // Invitar solo tiene sentido en un evento privado: uno publico ya tiene un
    // link que se puede compartir sin necesitar un token. Mismo 404 que
    // pendingRegistrations si no sos el creador, o si el evento no es privado
    // (lo decide @PreAuthorize, ver EventSecurity.isOwnerOfPrivate).
    @PreAuthorize("@eventSecurity.isOwnerOfPrivate(#id, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.GET)
    public ModelAndView inviteForm(@PathVariable final long id,
                                    @RequestParam(value = "sent", required = false) final String sentParam,
                                    @RequestParam(value = "alreadyInvited", required = false) final String alreadyInvitedParam,
                                    @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        return inviteFormView(event, currentUser, new InviteForm(), null, sentParam != null, alreadyInvitedParam != null);
    }

    @PreAuthorize("@eventSecurity.isOwnerOfPrivate(#id, authentication)")
    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.POST)
    public ModelAndView invite(@PathVariable final long id, @Valid final InviteForm form, final BindingResult errors,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        if (errors.hasErrors()) {
            return inviteFormView(event, currentUser, form, errors, false, false);
        }
        // Que el evento sea privado (y de quien invita) ya lo valida el service: si no, NOT_ALLOWED.
        final EventInvitationResult result = eventInvitationService.invite(id, currentUser.getId(), form.getEmail());
        if (result == EventInvitationResult.NOT_ALLOWED) {
            return new ModelAndView("redirect:/perfil/eventos");
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
            case NOT_ALLOWED -> throw new IllegalStateException("NOT_ALLOWED ya se filtro antes de llegar aca");
        };
    }

    private ModelAndView inviteFormView(final Event event, final User currentUser, final InviteForm form,
                                         final BindingResult errors, final boolean sent, final boolean alreadyInvited) {
        final List<EventInvitation> invitations = eventInvitationService.findByEvent(event.getId(), currentUser.getId());
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
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        return mav;
    }
}
