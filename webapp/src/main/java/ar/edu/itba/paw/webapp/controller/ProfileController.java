package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.ApprovalResult;
import ar.edu.itba.paw.services.EventInvitationResult;
import ar.edu.itba.paw.services.EventInvitationService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.ProfileUpdateResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewResult;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.services.UnregisterResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.ChangePasswordForm;
import ar.edu.itba.paw.webapp.form.InviteForm;
import ar.edu.itba.paw.webapp.form.ProfileForm;
import ar.edu.itba.paw.webapp.form.ReviewForm;
import ar.edu.itba.paw.webapp.util.Paging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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

    @Autowired
    public ProfileController(final UserService userService,
                              final RegistrationService registrationService, final EventService eventService,
                              final FavoriteService favoriteService, final ReviewService reviewService,
                              final EventInvitationService eventInvitationService,
                              final MessageSource messageSource, final InboxService inboxService) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.eventService = eventService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.eventInvitationService = eventInvitationService;
        this.messageSource = messageSource;
        this.inboxService = inboxService;
    }

    private String messageFor(final FieldError fieldError) {
        return messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
    }

    private Map<String, String> fieldErrorsOf(final BindingResult errors) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        return fieldErrors;
    }


    // Para el badge de "Mis inscripciones" en accountSidebar.tag: se pide en
    // todas las paginas de la cuenta, no solo en /perfil/inscripciones.
    private int registrationsCountFor(final User user) {
        return registrationService.findByUserId(user.getId()).size();
    }

    // Mismo motivo que registrationsCountFor: el badge de "Favoritos" en el
    // sidebar se pide en todas las paginas de la cuenta.
    private int favoritesCountFor(final User user) {
        return favoriteService.findEventIdsByUser(user.getId()).size();
    }

    // Mismo motivo que registrationsCountFor: el badge de "Mis eventos" en el
    // sidebar se pide en todas las paginas de la cuenta.
    private int eventsCountFor(final User user) {
        return eventService.findByCreatorId(user.getId()).size();
    }

    // Mismo motivo que registrationsCountFor: el badge de "Mis invitaciones"
    // en el sidebar se pide en todas las paginas de la cuenta.
    private int invitationsCountFor(final User user) {
        return eventInvitationService.findByUser(user.getId()).size();
    }

    // No hace falta antMatcher propio para /perfil ni /perfil/contrasena: no
    // estan en el permitAll de WebAuthConfig, asi que ya caen bajo
    // anyRequest().authenticated(). currentUser nunca es null aca (ver
    // ErrorAdvice.currentUserFromPrincipal): sin sesion, Spring Security ya
    // redirigio al login antes de que este metodo corra.
    @RequestMapping(value = "/perfil", method = RequestMethod.GET)
    public ModelAndView perfil(@RequestParam(value = "updated", required = false) final String updated,
                                @RequestParam(value = "passwordChanged", required = false) final String passwordChanged,
                                @ModelAttribute("currentUser") final User currentUser) {
        final ProfileForm profileForm = new ProfileForm();
        profileForm.setNombre(currentUser.getName());
        profileForm.setEmail(currentUser.getEmail());

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
                                       @ModelAttribute("currentUser") final User currentUser) {
        // El campo email llega vacio (esta disabled en perfil.jsp): lo completamos
        // con el de la sesion para que, si hay que re-mostrar el form, se vea igual.
        form.setEmail(currentUser.getEmail());

        if (!errors.hasErrors()) {
            final ProfileUpdateResult result = userService.updateProfile(currentUser.getId(), form.getNombre(), currentUser.getEmail());
            if (result == ProfileUpdateResult.EMAIL_TAKEN) {
                errors.rejectValue("email", "email.taken", "Ese email ya está en uso");
            }
        }

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
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
            return mav;
        }

        return new ModelAndView("redirect:/perfil?updated");
    }

    @RequestMapping(value = "/perfil/contrasena", method = RequestMethod.POST)
    public ModelAndView changePassword(@Valid final ChangePasswordForm form, final BindingResult errors,
                                        @ModelAttribute("currentUser") final User currentUser) {
        if (!errors.hasErrors()) {
            final boolean ok = userService.changePassword(currentUser.getId(), form.getCurrentPassword(), form.getNewPassword());
            if (!ok) {
                errors.rejectValue("currentPassword", "wrongCurrentPassword", "La contraseña actual no es correcta");
            }
        }

        if (errors.hasErrors()) {
            final ProfileForm profileForm = new ProfileForm();
            profileForm.setNombre(currentUser.getName());
            profileForm.setEmail(currentUser.getEmail());

            final ModelAndView mav = new ModelAndView("auth/perfil");
            mav.addObject("user", currentUser);
            mav.addObject("profileForm", profileForm);
            mav.addObject("changePasswordForm", form);
            mav.addObject("passwordFieldErrors", fieldErrorsOf(errors));
            mav.addObject("registrationsCount", registrationsCountFor(currentUser));
            mav.addObject("favoritesCount", favoritesCountFor(currentUser));
            mav.addObject("eventsCount", eventsCountFor(currentUser));
            mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
            return mav;
        }

        return new ModelAndView("redirect:/perfil?passwordChanged");
    }

    @RequestMapping(value = "/perfil/inscripciones", method = RequestMethod.GET)
    public ModelAndView misInscripciones(@ModelAttribute("currentUser") final User currentUser) {
        final List<Registration> registrations = registrationService.findByUserId(currentUser.getId());

        final Map<Long, Event> eventsById = new HashMap<>();
        for (final Registration registration : registrations) {
            eventService.findById(registration.getEventId()).ifPresent(event -> eventsById.put(event.getId(), event));
        }

        // Division por fecha: un evento que ya paso no se puede cancelar, asi
        // que ni siquiera le mostramos el boton (ver auth/inscripciones.jsp).
        final List<Registration> upcoming = new ArrayList<>();
        final List<Registration> past = new ArrayList<>();
        for (final Registration registration : registrations) {
            final Event event = eventsById.get(registration.getEventId());
            if (event == null) {
                continue;
            }
            (event.isFinished() ? past : upcoming).add(registration);
        }

        // Reseña propia de cada evento finalizado (si ya la mando): decide si
        // auth/inscripciones.jsp muestra el form de "Dejar reseña" o lo ya escrito.
        final Map<Long, Review> myReviewsByEventId = new HashMap<>();
        for (final Registration registration : past) {
            reviewService.findByEventAndUser(registration.getEventId(), currentUser.getId())
                    .ifPresent(review -> myReviewsByEventId.put(registration.getEventId(), review));
        }

        final ModelAndView mav = new ModelAndView("auth/inscripciones");
        mav.addObject("user", currentUser);
        mav.addObject("upcomingRegistrations", upcoming);
        mav.addObject("pastRegistrations", past);
        mav.addObject("eventsById", eventsById);
        mav.addObject("myReviewsByEventId", myReviewsByEventId);
        mav.addObject("registrationsCount", registrations.size());
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
    @RequestMapping(value = "/perfil/inscripciones/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelarInscripcion(@PathVariable final long id,
                                             @ModelAttribute("currentUser") final User currentUser,
                                             final RedirectAttributes redirectAttributes) {
        final UnregisterResult result = registrationService.cancel(id, currentUser.getId());
        // NOT_FOUND (no existe, o es de otro usuario) y REJECTED (estado terminal,
        // no se puede cancelar) no cancelaron nada: no corresponde mostrar el
        // cartel de "cancelaste tu inscripcion" como si hubiera pasado algo.
        final boolean cancelled = result != UnregisterResult.NOT_FOUND && result != UnregisterResult.REJECTED;
        redirectAttributes.addFlashAttribute("cancelled", cancelled);
        return new ModelAndView("redirect:/perfil/inscripciones");
    }

    // {id} es el id del EVENTO (no de la inscripcion): la reseña se identifica
    // por evento+usuario, no hace falta el id de la inscripcion para nada.
    // Los carteles van como flash attribute, igual que "cancelled" (ver arriba).
    @RequestMapping(value = "/perfil/inscripciones/{id}/resena", method = RequestMethod.POST)
    public ModelAndView leaveReview(@PathVariable final long id, @Valid final ReviewForm form,
                                     final BindingResult errors,
                                     @ModelAttribute("currentUser") final User currentUser,
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
            // Ya la habia dejado (doble click, o dos pestañas abiertas): cartel
            // propio en vez del error generico.
            case ALREADY_REVIEWED:
                redirectAttributes.addFlashAttribute("alreadyReviewed", true);
                return new ModelAndView("redirect:/perfil/inscripciones");
            default:
                redirectAttributes.addFlashAttribute("reviewError", true);
                return new ModelAndView("redirect:/perfil/inscripciones");
        }
    }

    @RequestMapping(value = "/perfil/favoritos", method = RequestMethod.GET)
    public ModelAndView misFavoritos(@ModelAttribute("currentUser") final User currentUser) {
        final List<Long> favoriteEventIds = favoriteService.findEventIdsByUser(currentUser.getId());

        final List<Event> favoriteEvents = new ArrayList<>();
        for (final Long eventId : favoriteEventIds) {
            eventService.findById(eventId).ifPresent(favoriteEvents::add);
        }

        final ModelAndView mav = new ModelAndView("auth/favoritos");
        mav.addObject("user", currentUser);
        mav.addObject("favoriteEvents", favoriteEvents);
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoriteEvents.size());
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        return mav;
    }

    // "Mis invitaciones": eventos privados a los que invitaron a esta usuaria,
    // separado de la bandeja (que mezcla todos los tipos de notificacion) para
    // acceder rapido justo a esto. Mismo patron que misFavoritos de arriba.
    @RequestMapping(value = "/perfil/invitaciones", method = RequestMethod.GET)
    public ModelAndView misInvitaciones(@ModelAttribute("currentUser") final User currentUser) {
        final List<EventInvitation> invitations = eventInvitationService.findByUser(currentUser.getId());

        final List<Event> invitedEvents = new ArrayList<>();
        for (final EventInvitation invitation : invitations) {
            eventService.findById(invitation.getEventId()).ifPresent(invitedEvents::add);
        }

        final ModelAndView mav = new ModelAndView("auth/invitaciones");
        mav.addObject("user", currentUser);
        mav.addObject("invitedEvents", invitedEvents);
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitedEvents.size());
        return mav;
    }

    // Bandeja de mensajes. Al abrirla se marcan todos como leidos, pero DESPUES
    // de traer la pagina: los que llegaron desde la ultima vez se muestran como
    // "nuevo" esta vez, y el numerito de la navbar ya sale en 0.
    @RequestMapping(value = "/perfil/bandeja", method = RequestMethod.GET)
    public ModelAndView bandeja(@RequestParam(value = "page", required = false) final String page,
                                @ModelAttribute("currentUser") final User currentUser) {
        final PageResult<InboxMessage> result = inboxService.findPage(currentUser.getId(), Paging.parsePage(page));

        // El titulo de cada evento, para armar el texto ("Ana se anoto en <titulo>").
        final Map<Long, Event> eventsById = new HashMap<>();
        for (final InboxMessage message : result.getContent()) {
            if (message.getEventId() != null && !eventsById.containsKey(message.getEventId())) {
                eventService.findById(message.getEventId()).ifPresent(e -> eventsById.put(e.getId(), e));
            }
        }
        inboxService.markAllRead(currentUser.getId());

        final ModelAndView mav = new ModelAndView("auth/bandeja");
        mav.addObject("user", currentUser);
        mav.addObject("messages", result.getContent());
        mav.addObject("eventsById", eventsById);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        return mav;
    }

    @RequestMapping(value = "/perfil/eventos", method = RequestMethod.GET)
    public ModelAndView misEventos(@ModelAttribute("currentUser") final User currentUser) {
        final List<Event> createdEvents = eventService.findByCreatorId(currentUser.getId());
        final List<Review> myReviews = reviewService.findByCreatorId(currentUser.getId());

        final ModelAndView mav = new ModelAndView("auth/eventos");
        mav.addObject("user", currentUser);
        mav.addObject("createdEvents", createdEvents);
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", createdEvents.size());
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
        final Map<Long, Integer> pendingCountsByEventId = new HashMap<>();
        for (final Event event : createdEvents) {
            if (event.isRequiresApproval()) {
                pendingCountsByEventId.put(event.getId(),
                        registrationService.findPendingByEvent(event.getId(), currentUser.getId()).size());
            }
        }
        mav.addObject("pendingCountsByEventId", pendingCountsByEventId);

        // Para el texto del modal de "Cancelar evento" (cuantos inscriptos
        // confirmados se van a enterar por mail): solo tiene sentido calcularlo
        // para eventos activos, uno ya cancelado no muestra el boton.
        final Map<Long, Long> confirmedCountsByEventId = new HashMap<>();
        for (final Event event : createdEvents) {
            if (!event.isCancelled()) {
                final long confirmed = registrationService.findByEventId(event.getId()).stream()
                        .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
                        .count();
                confirmedCountsByEventId.put(event.getId(), confirmed);
            }
        }
        mav.addObject("confirmedCountsByEventId", confirmedCountsByEventId);
        return mav;
    }

    // Busca el evento y confirma que quien pregunta es su creador. Si no existe,
    // o existe pero no es el dueño, mismo EventNotFoundException en los dos
    // casos -- no hay que confirmarle a nadie que un evento ajeno existe. Mismo
    // helper que EventController.eventForOwner().
    private Event eventForOwner(final long id, final User user) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        if (!Long.valueOf(user.getId()).equals(event.getCreatorId())) {
            throw new EventNotFoundException(id);
        }
        return event;
    }

    // No hace falta antMatcher propio: cae bajo anyRequest().authenticated().
    // Si no sos el creador de este evento, EventNotFoundException (mismo 404
    // que el resto de la app): no hay nada para vos aca, ni siquiera un 403
    // que confirme que el evento existe.
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView pendingRegistrations(@PathVariable final long id,
                                              @RequestParam(value = "approved", required = false) final String approvedParam,
                                              @RequestParam(value = "rejected", required = false) final String rejectedParam,
                                              @RequestParam(value = "full", required = false) final String fullParam,
                                              @ModelAttribute("currentUser") final User currentUser) {
        final Event event = eventForOwner(id, currentUser);

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
        mav.addObject("noSpotsLeft", availableSpots != null && availableSpots <= 0);
        mav.addObject("registrationsCount", registrationsCountFor(currentUser));
        mav.addObject("favoritesCount", favoritesCountFor(currentUser));
        mav.addObject("eventsCount", eventsCountFor(currentUser));
        mav.addObject("invitationsCount", invitationsCountFor(currentUser));
        return mav;
    }

    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/aceptar", method = RequestMethod.POST)
    public ModelAndView approveRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                             @ModelAttribute("currentUser") final User currentUser) {
        final ApprovalResult result = registrationService.approve(registrationId, currentUser.getId());
        final String param = result == ApprovalResult.FULL ? "?full" : result == ApprovalResult.APPROVED ? "?approved" : "";
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/inscriptos" + param);
    }

    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/rechazar", method = RequestMethod.POST)
    public ModelAndView rejectRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                            @ModelAttribute("currentUser") final User currentUser) {
        final boolean rejected = registrationService.reject(registrationId, currentUser.getId());
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/inscriptos" + (rejected ? "?rejected" : ""));
    }

    // Volver de "Ver inscriptos" (donde esta el boton Revocar) a esa misma lista.
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/revocar", method = RequestMethod.POST)
    public ModelAndView revokeRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                            @ModelAttribute("currentUser") final User currentUser) {
        final boolean revoked = registrationService.revoke(registrationId, currentUser.getId());
        return new ModelAndView("redirect:/eventos/" + id + "/inscriptos" + (revoked ? "?revoked" : ""));
    }

    // Invitar solo tiene sentido en un evento privado: uno publico ya tiene un
    // link que se puede compartir sin necesitar un token. Mismo 404 que
    // pendingRegistrations si no sos el creador, o si el evento no es privado.
    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.GET)
    public ModelAndView inviteForm(@PathVariable final long id,
                                    @RequestParam(value = "sent", required = false) final String sentParam,
                                    @RequestParam(value = "alreadyInvited", required = false) final String alreadyInvitedParam,
                                    @ModelAttribute("currentUser") final User currentUser) {
        final Event event = eventForOwner(id, currentUser);
        if (!event.isPrivate()) {
            throw new EventNotFoundException(id);
        }
        return inviteFormView(event, currentUser, new InviteForm(), null, sentParam != null, alreadyInvitedParam != null);
    }

    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.POST)
    public ModelAndView invite(@PathVariable final long id, @Valid final InviteForm form, final BindingResult errors,
                                @ModelAttribute("currentUser") final User currentUser) {
        final Event event = eventForOwner(id, currentUser);
        if (!event.isPrivate()) {
            throw new EventNotFoundException(id);
        }
        if (errors.hasErrors()) {
            return inviteFormView(event, currentUser, form, errors, false, false);
        }
        final EventInvitationResult result = eventInvitationService.invite(id, currentUser.getId(), form.getEmail());
        final String param = result == EventInvitationResult.ALREADY_INVITED ? "alreadyInvited"
                : result == EventInvitationResult.FULL ? "full"
                : result == EventInvitationResult.SELF_INVITE ? "selfInvite" : "sent";
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/invitar?" + param);
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
