package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
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
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.ChangePasswordForm;
import ar.edu.itba.paw.webapp.form.InviteForm;
import ar.edu.itba.paw.webapp.form.ProfileForm;
import ar.edu.itba.paw.webapp.form.ReviewForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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

    private User currentUser(final Principal principal) {
        return userService.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado sin fila en la base: " + principal.getName()));
    }

    // Para el badge de "Mis inscripciones" en accountSidebar.tag: se pide en
    // todas las paginas de la cuenta, no solo en /perfil/inscripciones.
    private int registrationsCountFor(final User user) {
        return registrationService.findByEmail(user.getEmail()).size();
    }

    // Mismo motivo que registrationsCountFor: el badge de "Favoritos" en el
    // sidebar se pide en todas las paginas de la cuenta.
    private int favoritesCountFor(final User user) {
        return favoriteService.findEventIdsByUser(user.getId()).size();
    }

    // Mismo motivo que registrationsCountFor: el badge de "Mis eventos" en el
    // sidebar se pide en todas las paginas de la cuenta.
    private int eventsCountFor(final User user) {
        return eventService.findByCreatorEmail(user.getEmail()).size();
    }

    // No hace falta antMatcher propio para /perfil ni /perfil/contrasena: no
    // estan en el permitAll de WebAuthConfig, asi que ya caen bajo
    // anyRequest().authenticated().
    @RequestMapping(value = "/perfil", method = RequestMethod.GET)
    public ModelAndView perfil(@RequestParam(value = "updated", required = false) final String updated,
                                @RequestParam(value = "passwordChanged", required = false) final String passwordChanged,
                                final Principal principal) {
        final User user = currentUser(principal);
        final ProfileForm profileForm = new ProfileForm();
        profileForm.setNombre(user.getName());
        profileForm.setEmail(user.getEmail());

        final ModelAndView mav = new ModelAndView("auth/perfil");
        mav.addObject("user", user);
        mav.addObject("profileForm", profileForm);
        mav.addObject("changePasswordForm", new ChangePasswordForm());
        mav.addObject("profileUpdated", updated != null);
        mav.addObject("passwordChanged", passwordChanged != null);
        mav.addObject("registrationsCount", registrationsCountFor(user));
        mav.addObject("favoritesCount", favoritesCountFor(user));
        mav.addObject("eventsCount", eventsCountFor(user));
        return mav;
    }

    @RequestMapping(value = "/perfil", method = RequestMethod.POST)
    public ModelAndView updateProfile(@Valid final ProfileForm form, final BindingResult errors,
                                       final Principal principal) {
        final User user = currentUser(principal);
        // El campo email llega vacio (esta disabled en perfil.jsp): lo completamos
        // con el de la sesion para que, si hay que re-mostrar el form, se vea igual.
        form.setEmail(user.getEmail());

        if (!errors.hasErrors()) {
            final ProfileUpdateResult result = userService.updateProfile(user.getId(), form.getNombre(), user.getEmail());
            if (result == ProfileUpdateResult.EMAIL_TAKEN) {
                errors.rejectValue("email", "email.taken", "Ese email ya está en uso");
            }
        }

        if (errors.hasErrors()) {
            final ModelAndView mav = new ModelAndView("auth/perfil");
            mav.addObject("user", user);
            mav.addObject("profileForm", form);
            mav.addObject("profileFieldErrors", fieldErrorsOf(errors));
            mav.addObject("changePasswordForm", new ChangePasswordForm());
            mav.addObject("registrationsCount", registrationsCountFor(user));
            mav.addObject("favoritesCount", favoritesCountFor(user));
            mav.addObject("eventsCount", eventsCountFor(user));
            return mav;
        }

        return new ModelAndView("redirect:/perfil?updated");
    }

    @RequestMapping(value = "/perfil/contrasena", method = RequestMethod.POST)
    public ModelAndView changePassword(@Valid final ChangePasswordForm form, final BindingResult errors,
                                        final Principal principal) {
        final User user = currentUser(principal);

        if (!errors.hasErrors() && !form.getNewPassword().equals(form.getConfirmarPassword())) {
            errors.rejectValue("confirmarPassword", "PasswordMismatch", "Las contraseñas no coinciden");
        }
        if (!errors.hasErrors()) {
            final boolean ok = userService.changePassword(user.getId(), form.getCurrentPassword(), form.getNewPassword());
            if (!ok) {
                errors.rejectValue("currentPassword", "wrongCurrentPassword", "La contraseña actual no es correcta");
            }
        }

        if (errors.hasErrors()) {
            final ProfileForm profileForm = new ProfileForm();
            profileForm.setNombre(user.getName());
            profileForm.setEmail(user.getEmail());

            final ModelAndView mav = new ModelAndView("auth/perfil");
            mav.addObject("user", user);
            mav.addObject("profileForm", profileForm);
            mav.addObject("changePasswordForm", form);
            mav.addObject("passwordFieldErrors", fieldErrorsOf(errors));
            mav.addObject("registrationsCount", registrationsCountFor(user));
            mav.addObject("favoritesCount", favoritesCountFor(user));
            mav.addObject("eventsCount", eventsCountFor(user));
            return mav;
        }

        return new ModelAndView("redirect:/perfil?passwordChanged");
    }

    @RequestMapping(value = "/perfil/inscripciones", method = RequestMethod.GET)
    public ModelAndView misInscripciones(@RequestParam(value = "cancelled", required = false) final String cancelled,
                                          @RequestParam(value = "reviewed", required = false) final String reviewed,
                                          @RequestParam(value = "reviewError", required = false) final String reviewError,
                                          @RequestParam(value = "alreadyReviewed", required = false) final String alreadyReviewed,
                                          final Principal principal) {
        final User user = currentUser(principal);
        final List<Registration> registrations = registrationService.findByEmail(user.getEmail());

        final Map<Long, Event> eventsById = new HashMap<>();
        for (final Registration registration : registrations) {
            eventService.findById(registration.getEventId()).ifPresent(event -> eventsById.put(event.getId(), event));
        }

        // Division por fecha: un evento que ya paso no se puede cancelar, asi
        // que ni siquiera le mostramos el boton (ver auth/inscripciones.jsp).
        final Date now = new Date();
        final List<Registration> upcoming = new ArrayList<>();
        final List<Registration> past = new ArrayList<>();
        for (final Registration registration : registrations) {
            final Event event = eventsById.get(registration.getEventId());
            if (event == null) {
                continue;
            }
            (event.getEventDate().before(now) ? past : upcoming).add(registration);
        }

        // Reseña propia de cada evento finalizado (si ya la mando): decide si
        // auth/inscripciones.jsp muestra el form de "Dejar reseña" o lo ya escrito.
        final Map<Long, Review> myReviewsByEventId = new HashMap<>();
        for (final Registration registration : past) {
            reviewService.findByEventAndUser(registration.getEventId(), user.getId())
                    .ifPresent(review -> myReviewsByEventId.put(registration.getEventId(), review));
        }

        final ModelAndView mav = new ModelAndView("auth/inscripciones");
        mav.addObject("user", user);
        mav.addObject("upcomingRegistrations", upcoming);
        mav.addObject("pastRegistrations", past);
        mav.addObject("eventsById", eventsById);
        mav.addObject("myReviewsByEventId", myReviewsByEventId);
        mav.addObject("cancelled", cancelled != null);
        mav.addObject("reviewed", reviewed != null);
        mav.addObject("reviewError", reviewError != null);
        mav.addObject("alreadyReviewed", alreadyReviewed != null);
        mav.addObject("registrationsCount", registrations.size());
        mav.addObject("favoritesCount", favoritesCountFor(user));
        mav.addObject("eventsCount", eventsCountFor(user));
        return mav;
    }

    @RequestMapping(value = "/perfil/inscripciones/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelarInscripcion(@PathVariable final long id, final Principal principal) {
        registrationService.cancel(id, principal.getName());
        return new ModelAndView("redirect:/perfil/inscripciones?cancelled");
    }

    // {id} es el id del EVENTO (no de la inscripcion): la reseña se identifica
    // por evento+usuario, no hace falta el id de la inscripcion para nada.
    @RequestMapping(value = "/perfil/inscripciones/{id}/resena", method = RequestMethod.POST)
    public ModelAndView leaveReview(@PathVariable final long id, @Valid final ReviewForm form,
                                     final BindingResult errors, final Principal principal) {
        if (errors.hasErrors()) {
            return new ModelAndView("redirect:/perfil/inscripciones?reviewError");
        }
        final User user = currentUser(principal);
        final ReviewResult result;
        try {
            result = reviewService.create(id, user, form.getRating(), form.getComment());
        } catch (final DataIntegrityViolationException e) {
            // Dos envios al mismo tiempo: el segundo choca con el UNIQUE(event_id, user_id).
            return new ModelAndView("redirect:/perfil/inscripciones?alreadyReviewed");
        }
        switch (result) {
            case CREATED:
                return new ModelAndView("redirect:/perfil/inscripciones?reviewed");
            // Ya la habia dejado (doble click, o dos pestañas abiertas): cartel
            // propio en vez del error generico.
            case ALREADY_REVIEWED:
                return new ModelAndView("redirect:/perfil/inscripciones?alreadyReviewed");
            default:
                return new ModelAndView("redirect:/perfil/inscripciones?reviewError");
        }
    }

    @RequestMapping(value = "/perfil/favoritos", method = RequestMethod.GET)
    public ModelAndView misFavoritos(final Principal principal) {
        final User user = currentUser(principal);
        final List<Long> favoriteEventIds = favoriteService.findEventIdsByUser(user.getId());

        final List<Event> favoriteEvents = new ArrayList<>();
        for (final Long eventId : favoriteEventIds) {
            eventService.findById(eventId).ifPresent(favoriteEvents::add);
        }

        final ModelAndView mav = new ModelAndView("auth/favoritos");
        mav.addObject("user", user);
        mav.addObject("favoriteEvents", favoriteEvents);
        mav.addObject("registrationsCount", registrationsCountFor(user));
        mav.addObject("favoritesCount", favoriteEvents.size());
        mav.addObject("eventsCount", eventsCountFor(user));
        return mav;
    }

    // Bandeja de mensajes. Al abrirla se marcan todos como leidos, pero DESPUES
    // de traer la pagina: los que llegaron desde la ultima vez se muestran como
    // "nuevo" esta vez, y el numerito de la navbar ya sale en 0.
    @RequestMapping(value = "/perfil/bandeja", method = RequestMethod.GET)
    public ModelAndView bandeja(@RequestParam(value = "page", required = false) final String page,
                                final Principal principal) {
        final User user = currentUser(principal);
        final PageResult<InboxMessage> result = inboxService.findPage(user.getEmail(), parsePage(page));

        // El titulo de cada evento, para armar el texto ("Ana se anoto en <titulo>").
        final Map<Long, Event> eventsById = new HashMap<>();
        for (final InboxMessage message : result.getContent()) {
            if (message.getEventId() != null && !eventsById.containsKey(message.getEventId())) {
                eventService.findById(message.getEventId()).ifPresent(e -> eventsById.put(e.getId(), e));
            }
        }
        inboxService.markAllRead(user.getEmail());

        final ModelAndView mav = new ModelAndView("auth/bandeja");
        mav.addObject("user", user);
        mav.addObject("messages", result.getContent());
        mav.addObject("eventsById", eventsById);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("registrationsCount", registrationsCountFor(user));
        mav.addObject("favoritesCount", favoritesCountFor(user));
        mav.addObject("eventsCount", eventsCountFor(user));
        return mav;
    }

    // Un numero de pagina roto (?page=abc, ?page=-3) no es un error: se muestra la primera.
    private static int parsePage(final String value) {
        try {
            return value == null ? 1 : Math.max(Integer.parseInt(value), 1);
        } catch (final NumberFormatException e) {
            return 1;
        }
    }

    @RequestMapping(value = "/perfil/eventos", method = RequestMethod.GET)
    public ModelAndView misEventos(final Principal principal) {
        final User user = currentUser(principal);
        final List<Event> createdEvents = eventService.findByCreatorEmail(user.getEmail());
        final List<Review> myReviews = reviewService.findByCreatorEmail(user.getEmail());

        final ModelAndView mav = new ModelAndView("auth/eventos");
        mav.addObject("user", user);
        mav.addObject("createdEvents", createdEvents);
        mav.addObject("registrationsCount", registrationsCountFor(user));
        mav.addObject("favoritesCount", favoritesCountFor(user));
        mav.addObject("eventsCount", createdEvents.size());
        mav.addObject("creatorAverageRating", reviewService.findCreatorAverageRating(user.getEmail()));
        mav.addObject("creatorReviewCount", myReviews.size());
        if (!myReviews.isEmpty()) {
            // Para el popup "Ver mis reseñas": mismo patron que
            // EventController.addCreatorReputation, pero disparado desde ac.
            mav.addObject("myCreatorReviews", myReviews);
            final Map<Long, Event> reviewEventsById = new HashMap<>();
            for (final Review review : myReviews) {
                eventService.findById(review.getEventId()).ifPresent(e -> reviewEventsById.put(e.getId(), e));
            }
            mav.addObject("reviewEventsById", reviewEventsById);
        }
        // Badge "N pendientes" por evento en eventRow.tag: solo tiene sentido
        // para eventos con requiresApproval, no vale la pena consultar el resto.
        final Map<Long, Integer> pendingCountsByEventId = new HashMap<>();
        for (final Event event : createdEvents) {
            if (event.isRequiresApproval()) {
                pendingCountsByEventId.put(event.getId(),
                        registrationService.findPendingByEvent(event.getId(), user.getEmail()).size());
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

    // No hace falta antMatcher propio: cae bajo anyRequest().authenticated().
    // Si no sos el creador de este evento, EventNotFoundException (mismo 404
    // que el resto de la app): no hay nada para vos aca, ni siquiera un 403
    // que confirme que el evento existe.
    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView pendingRegistrations(@PathVariable final long id,
                                              @RequestParam(value = "approved", required = false) final String approvedParam,
                                              @RequestParam(value = "rejected", required = false) final String rejectedParam,
                                              final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        final User user = currentUser(principal);
        if (!event.getCreatorEmail().equalsIgnoreCase(user.getEmail())) {
            throw new EventNotFoundException(id);
        }

        final ModelAndView mav = new ModelAndView("auth/inscriptos");
        mav.addObject("user", user);
        mav.addObject("event", event);
        mav.addObject("pendingRegistrations", registrationService.findPendingByEvent(id, user.getEmail()));
        mav.addObject("approved", approvedParam != null);
        mav.addObject("rejected", rejectedParam != null);
        mav.addObject("registrationsCount", registrationsCountFor(user));
        mav.addObject("favoritesCount", favoritesCountFor(user));
        mav.addObject("eventsCount", eventsCountFor(user));
        return mav;
    }

    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/aceptar", method = RequestMethod.POST)
    public ModelAndView approveRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                             final Principal principal) {
        registrationService.approve(registrationId, principal.getName());
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/inscriptos?approved");
    }

    @RequestMapping(value = "/perfil/eventos/{id}/inscriptos/{registrationId}/rechazar", method = RequestMethod.POST)
    public ModelAndView rejectRegistration(@PathVariable final long id, @PathVariable final long registrationId,
                                            final Principal principal) {
        registrationService.reject(registrationId, principal.getName());
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/inscriptos?rejected");
    }

    // Invitar solo tiene sentido en un evento privado: uno publico ya tiene un
    // link que se puede compartir sin necesitar un token. Mismo 404 que
    // pendingRegistrations si no sos el creador, o si el evento no es privado.
    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.GET)
    public ModelAndView inviteForm(@PathVariable final long id,
                                    @RequestParam(value = "sent", required = false) final String sentParam,
                                    @RequestParam(value = "alreadyInvited", required = false) final String alreadyInvitedParam,
                                    final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        final User user = currentUser(principal);
        if (!event.getCreatorEmail().equalsIgnoreCase(user.getEmail()) || !event.isPrivate()) {
            throw new EventNotFoundException(id);
        }
        return inviteFormView(event, user, new InviteForm(), null, sentParam != null, alreadyInvitedParam != null);
    }

    @RequestMapping(value = "/perfil/eventos/{id}/invitar", method = RequestMethod.POST)
    public ModelAndView invite(@PathVariable final long id, @Valid final InviteForm form, final BindingResult errors,
                                final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        final User user = currentUser(principal);
        if (!event.getCreatorEmail().equalsIgnoreCase(user.getEmail()) || !event.isPrivate()) {
            throw new EventNotFoundException(id);
        }
        if (errors.hasErrors()) {
            return inviteFormView(event, user, form, errors, false, false);
        }
        final EventInvitationResult result = eventInvitationService.invite(id, user.getEmail(), form.getEmail());
        final String param = result == EventInvitationResult.ALREADY_INVITED ? "alreadyInvited" : "sent";
        return new ModelAndView("redirect:/perfil/eventos/" + id + "/invitar?" + param);
    }

    private ModelAndView inviteFormView(final Event event, final User user, final InviteForm form,
                                         final BindingResult errors, final boolean sent, final boolean alreadyInvited) {
        final List<EventInvitation> invitations = eventInvitationService.findByEvent(event.getId(), user.getEmail());
        final ModelAndView mav = new ModelAndView("auth/invitar");
        mav.addObject("user", user);
        mav.addObject("event", event);
        mav.addObject("inviteForm", form);
        if (errors != null) {
            mav.addObject("fieldErrors", fieldErrorsOf(errors));
        }
        mav.addObject("invitations", invitations);
        mav.addObject("sent", sent);
        mav.addObject("alreadyInvited", alreadyInvited);
        mav.addObject("registrationsCount", registrationsCountFor(user));
        mav.addObject("favoritesCount", favoritesCountFor(user));
        mav.addObject("eventsCount", eventsCountFor(user));
        return mav;
    }
}
