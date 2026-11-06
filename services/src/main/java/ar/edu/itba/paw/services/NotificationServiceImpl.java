package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceImpl.class);

    // Fallback cuando el destinatario todavia no eligio idioma a mano
    private static final Locale DEFAULT_LOCALE = new Locale("es");
    private static final Locale MONEY_LOCALE = new Locale("es", "AR");

    private final MailService mailService;
    private final MessageSource messageSource;
    private final UserDao userDao;

    // Los links de los mails: el service pide cada uno por lo que es y no conoce rutas ni servidor.
    private final MailLinks links;

    @Autowired
    public NotificationServiceImpl(final MailService mailService,
                                   @Qualifier("mailMessageSource") final MessageSource messageSource,
                                   final UserDao userDao, final MailLinks links) {
        this.links = links;
        this.mailService = mailService;
        this.messageSource = messageSource;
        this.userDao = userDao;
    }

    @Async
    @Override
    public void notifyEventPendingApproval(final Event event, final String creatorName) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creatorName, event.getCreatorEmail()));

            mailService.send(event.getCreatorEmail(),
                    "event-pending-approval",
                    "mail.eventPendingApproval.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de evento pendiente de aprobacion, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationConfirmed(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            // El CTA manda a "Mis inscripciones", no al evento: quien se anoto ya
            // esta viendo estos datos en este mismo mail, lo util es llevarlo a
            // donde puede gestionar (o mas adelante reseñar) sus inscripciones.
            variables.put("registrationsUrl", links.registrations());

            mailService.send(registration.getEmail(),
                    "registration-confirmed",
                    "mail.registrationConfirmed.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion confirmada, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyNewRegistration(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());

            mailService.send(event.getCreatorEmail(),
                    "new-registration",
                    "mail.newRegistration.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion nueva, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationPending(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrationsUrl", links.registrations());

            mailService.send(registration.getEmail(),
                    "registration-pending",
                    "mail.registrationPending.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion pendiente, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyNewPendingRegistration(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());
            // Manda directo al evento: ahi vive la pantalla de aceptar/rechazar
            // (ver ProfileController), no hace falta un link aparte.
            variables.put("reviewUrl", links.attendees(event.getId()));

            mailService.send(event.getCreatorEmail(),
                    "new-pending-registration",
                    "mail.newPendingRegistration.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de inscripcion pendiente al creador, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationApproved(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrationsUrl", links.registrations());

            mailService.send(registration.getEmail(),
                    "registration-approved",
                    "mail.registrationApproved.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion aprobada, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationRejected(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("eventsUrl", links.events());

            mailService.send(registration.getEmail(),
                    "registration-rejected",
                    "mail.registrationRejected.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion rechazada, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventInvitation(final Event event, final EventInvitation invitation) {
        try {
            // invitation.getUserId() es optimista (ver el modelo): si es null
            // (todavia no tiene cuenta) localeForUserId() cae en DEFAULT_LOCALE,
            // que es exactamente lo correcto para alguien sin preferencia guardada.
            final Locale locale = localeForUserId(invitation.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            // No hay nombre: a esta altura solo se conoce el mail al que se invito.
            variables.put("personName", displayName(null, invitation.getEmail()));

            mailService.send(invitation.getEmail(),
                    "event-invitation",
                    "mail.eventInvitation.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la invitacion al evento, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyWaitlistJoined(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            // Mismo CTA que el mail de inscripcion confirmada: desde "Mis
            // inscripciones" puede salirse de la lista si ya no le interesa.
            variables.put("registrationsUrl", links.registrations());

            mailService.send(registration.getEmail(),
                    "waitlist-joined",
                    "mail.waitlistJoined.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de lista de espera, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyNewWaitlistEntry(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());

            mailService.send(event.getCreatorEmail(),
                    "waitlist-new-entry",
                    "mail.waitlistNewEntry.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de lista de espera al creador, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyWaitlistPromoted(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrationsUrl", links.registrations());

            mailService.send(registration.getEmail(),
                    "waitlist-promoted",
                    "mail.waitlistPromoted.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de lugar de la lista de espera, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationRevoked(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("eventsUrl", links.events());

            mailService.send(registration.getEmail(),
                    "registration-revoked",
                    "mail.registrationRevoked.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de lugar revocado, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventFull(final Event event) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));

            mailService.send(event.getCreatorEmail(),
                    "event-full",
                    "mail.eventFull.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento completo, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationCancelled(final Event event, final Registration registration,
                                             final boolean offeredToWaitlist) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());
            // La plantilla elige entre dos parrafos con este booleano, asi los dos
            // textos siguen viviendo en el archivo de mensajes y no en el codigo.
            variables.put("offeredToWaitlist", offeredToWaitlist);

            mailService.send(event.getCreatorEmail(),
                    "registration-cancelled",
                    "mail.registrationCancelled.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de baja al creador, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyAccountVerification(final User user, final String token, final String redirectAfterConfirm) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            // Si venia de algun lado (una invitacion a un evento privado), el link lo lleva: al confirmar y
            // loguearse vuelve ahi en vez de al home. Como viaja eso en la URL lo sabe MailLinks, no el service.
            variables.put("confirmUrl", links.confirmAccount(token, redirectAfterConfirm));

            mailService.send(user.getEmail(),
                    "account-verification",
                    "mail.accountVerification.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el mail de confirmacion de cuenta, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyPasswordReset(final User user, final String token) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            variables.put("resetUrl", links.resetPassword(token));

            mailService.send(user.getEmail(),
                    "password-reset",
                    "mail.passwordReset.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el mail de restablecer contraseña, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventCancelled(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("eventsUrl", links.events());

            mailService.send(registration.getEmail(),
                    "event-cancelled",
                    "mail.eventCancelled.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento cancelado, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventUpdated(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));

            mailService.send(registration.getEmail(),
                    "event-updated",
                    "mail.eventUpdated.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento editado, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventReminder(final Event event, final Registration registration) {
        try {
            final Locale locale = localeForUserId(registration.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));

            mailService.send(registration.getEmail(),
                    "event-reminder",
                    "mail.eventReminder.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el recordatorio de 24 hs, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyOrganizerEventReminder(final Event event) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("attendeesUrl", links.attendees(event.getId()));

            mailService.send(event.getCreatorEmail(),
                    "event-reminder-organizer",
                    "mail.organizerReminder.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el recordatorio de 24 hs al organizador, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyNewQuestion(final Event event, final EventQuestion question,
                                  final String askerName, final String askerEmail) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("askerName", displayName(askerName, askerEmail));
            variables.put("questionText", question.getText());
            // Directo a la sección de preguntas del detalle.
            variables.put("eventUrl", links.eventQuestions(event.getId()));

            mailService.send(event.getCreatorEmail(),
                    "new-question",
                    "mail.newQuestion.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de pregunta nueva, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyQuestionAnswered(final Event event, final EventQuestion question,
                                       final String askerName, final String askerEmail) {
        try {
            final Locale locale = localeForUserId(question.getUserId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(askerName, askerEmail));
            variables.put("questionText", question.getText());
            variables.put("answerText", question.getAnswer());
            variables.put("eventUrl", links.eventQuestions(event.getId()));

            mailService.send(askerEmail,
                    "question-answered",
                    "mail.questionAnswered.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de pregunta respondida, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventRemovedByAdmin(final Event event, final String reason) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("reason", reason);
            variables.put("eventsUrl", links.events());

            mailService.send(event.getCreatorEmail(),
                    "event-removed-by-admin",
                    "mail.eventRemovedByAdmin.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento removido por un admin, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventApproved(final Event event) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));

            mailService.send(event.getCreatorEmail(),
                    "event-approved",
                    "mail.eventApproved.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento aprobado, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifySavedSearchMatches(final User user, final long savedSearchId, final Event example,
                                         final long newEventsCount) {
        try {
            final Locale locale = localeForUserId(user.getId());
            final Map<String, Object> variables = eventVariables(example, locale);
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            variables.put("newEventsCount", newEventsCount);
            variables.put("unsubscribeUrl", links.unsubscribeSavedSearch(savedSearchId));

            mailService.send(user.getEmail(),
                    "saved-search-matches",
                    "mail.savedSearchMatches.subject",
                    new Object[]{ newEventsCount },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de busqueda guardada, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventRejected(final Event event, final String reason) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("reason", reason);
            // El evento rechazado nunca llega a ser accesible: el CTA va al
            // listado general, no a eventUrl (mismo criterio que
            // notifyEventRemovedByAdmin).
            variables.put("eventsUrl", links.events());

            mailService.send(event.getCreatorEmail(),
                    "event-rejected",
                    "mail.eventRejected.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento rechazado, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventChangesRequested(final Event event, final String message) {
        try {
            final Locale locale = localeForUserId(event.getCreatorId());
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("message", message);
            // Al editar directo, no al detalle: es lo que la persona tiene que hacer.
            variables.put("editEventUrl", links.editEvent(event.getId()));

            mailService.send(event.getCreatorEmail(),
                    "event-changes-requested",
                    "mail.eventChangesRequested.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de cambios pedidos por un admin, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyUserSuspended(final User user) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));

            mailService.send(user.getEmail(),
                    "account-suspended",
                    "mail.accountSuspended.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de cuenta suspendida, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyUserReactivated(final User user) {
        try {
            final Locale locale = localeFor(user);
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            variables.put("loginUrl", links.login());

            mailService.send(user.getEmail(),
                    "account-reactivated",
                    "mail.accountReactivated.subject",
                    null,
                    variables,
                    locale);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de cuenta reactivada, usuario {}", user.getId(), e);
        }
    }

    /**
     * Los datos del evento que usan casi todas las plantillas. Se arma una sola
     * vez aca en vez de repetirlo en cada metodo. El locale es el del
     * DESTINATARIO de ese mail puntual (ver localeFor()/localeForUserId()), no
     * uno fijo: por eso lo recibe como parametro en vez de usar DEFAULT_LOCALE.
     */
    private Map<String, Object> eventVariables(final Event event, final Locale locale) {
        final Map<String, Object> variables = new HashMap<>();
        variables.put("eventTitle", event.getTitle());
        variables.put("eventDate", formatDate(event, locale));
        variables.put("eventPlace", formatPlace(event));
        variables.put("eventPrice", formatPrice(event, locale));
        // El link tiene que ser absoluto: el mail se abre en Gmail, no en la app.
        variables.put("eventUrl", links.event(event.getId()));
        return variables;
    }

    private String formatDate(final Event event, final Locale locale) {
        if (event.getEventDate() == null) {
            return "";
        }
        // FULL para la fecha ("sabado, 12 de septiembre de 2026") y SHORT para
        // la hora ("21:00"). El formato lo elige Java segun el idioma.
        return DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, locale)
                .format(event.getEventDate());
    }

    private String formatPlace(final Event event) {
        if (event.getNeighborhood() == null) {
            return event.getAddress();
        }
        return event.getAddress() + ", " + event.getNeighborhood().getDisplayName();
    }

    private String formatPrice(final Event event, final Locale locale) {
        if (event.isFree()) {
            // "Gratis" / "Free" sale del archivo de textos, como todo lo demas.
            return messageSource.getMessage("mail.event.free", null, locale);
        }
        return NumberFormat.getCurrencyInstance(MONEY_LOCALE).format(event.getPrice());
    }

    /**
     * Con que nombre saludamos. Primero el que nos pasan; si no hay, el nombre
     * de la cuenta con ese email (el que eligio al registrarse); y recien si
     * tampoco existe (invitado sin cuenta), lo que va antes del arroba: es
     * mejor "Hola sol," que "Hola null,".
     */
    private String displayName(final String name, final String email) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        if (email == null) {
            return "";
        }
        final String accountName = userDao.findByEmail(email)
                .map(User::getName)
                .filter(n -> !n.trim().isEmpty())
                .map(String::trim)
                .orElse(null);
        if (accountName != null) {
            return accountName;
        }
        final int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /**
     * El idioma guardado de un destinatario que YA tenemos cargado (no hace
     * falta ir a buscarlo por id).
     */
    // protected (no private): NotificationServiceImplTest, en el mismo paquete,
    // lo llama directo para probar la resolucion de locale sin tener que pasar
    // por un metodo void y espiar que le llego a MailService.
    protected Locale localeFor(final User user) {
        return toLocale(user.getLocale());
    }

    /**
     * Igual que localeFor(User), pero a partir de un id que puede no
     * corresponder a nadie con cuenta todavia: userId null (invitado sin
     * cuenta, ver EventInvitation.userId) o sin fila (no deberia pasar, pero
     * no vale la pena reventar un mail por eso) caen los dos en DEFAULT_LOCALE.
     */
    // protected, mismo motivo que localeFor(User).
    protected Locale localeForUserId(final Long userId) {
        if (userId == null) {
            return DEFAULT_LOCALE;
        }
        return userDao.findById(userId).map(User::getLocale).map(this::toLocale).orElse(DEFAULT_LOCALE);
    }

    private Locale toLocale(final String languageTag) {
        return (languageTag == null || languageTag.isEmpty()) ? DEFAULT_LOCALE : new Locale(languageTag);
    }
}
