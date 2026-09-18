package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.ProfileUpdateResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.ChangePasswordForm;
import ar.edu.itba.paw.webapp.form.ProfileForm;
import ar.edu.itba.paw.webapp.form.ReviewForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
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
    private final UserDetailsService userDetailsService;
    private final RegistrationService registrationService;
    private final EventService eventService;
    private final FavoriteService favoriteService;
    private final ReviewService reviewService;
    private final MessageSource messageSource;

    @Autowired
    public ProfileController(final UserService userService, final UserDetailsService userDetailsService,
                              final RegistrationService registrationService, final EventService eventService,
                              final FavoriteService favoriteService, final ReviewService reviewService,
                              final MessageSource messageSource) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.registrationService = registrationService;
        this.eventService = eventService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.messageSource = messageSource;
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

    // Promedio de estrellas de este creador, juntando las reseñas de TODOS sus
    // eventos (no solo uno): es el "puntaje" que ve en Mis eventos.
    private static double averageRating(final List<Review> reviews) {
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0);
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

        if (!errors.hasErrors()) {
            final ProfileUpdateResult result = userService.updateProfile(user.getId(), form.getNombre(), form.getEmail());
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

        // El email pudo haber cambiado: refrescamos el Authentication de la sesion
        // actual con el nuevo username, si no principal.getName() queda apuntando
        // al email viejo hasta el proximo login (y findByEmail en el proximo
        // pedido a /perfil ya no lo encontraria).
        refreshAuthentication(form.getEmail().trim().toLowerCase());
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
        final boolean created = reviewService.create(id, user, form.getRating(), form.getComment());
        return new ModelAndView("redirect:/perfil/inscripciones" + (created ? "?reviewed" : "?reviewError"));
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
        mav.addObject("creatorAverageRating", averageRating(myReviews));
        mav.addObject("creatorReviewCount", myReviews.size());
        return mav;
    }

    private void refreshAuthentication(final String newEmail) {
        final UserDetails updatedDetails = userDetailsService.loadUserByUsername(newEmail);
        final Authentication current = SecurityContextHolder.getContext().getAuthentication();
        final Authentication refreshed = new UsernamePasswordAuthenticationToken(
                updatedDetails, current.getCredentials(), updatedDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(refreshed);
    }
}
