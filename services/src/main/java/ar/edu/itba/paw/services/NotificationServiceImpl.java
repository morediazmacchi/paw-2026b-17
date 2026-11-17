package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.FollowerContact;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceImpl.class);

    // Fallback cuando el destinatario todavia no eligio idioma a mano
    private static final Locale DEFAULT_LOCALE = new Locale("es");

    private final MailService mailService;
    private final MessageSource messageSource;
    private final UserService userService;

    // Los links de los mails: el service pide cada uno por lo que es y no conoce rutas ni servidor.
    private final MailLinks links;

    @Autowired
    public NotificationServiceImpl(final MailService mailService,
                                   @Qualifier("mailMessageSource") final MessageSource messageSource,
                                   final UserService userService, final MailLinks links) {
        this.links = links;
        this.mailService = mailService;
        this.messageSource = messageSource;
        this.userService = userService;
    }

    @Async
    @Override
    public void notifyEventPendingApproval(final Event event, final String creatorName) {
        attempt("Fallo la notificacion de evento pendiente de aprobacion", event, () -> {
            final Locale locale = localeForUserId(event.getCreatorId());
            sendEventMail(event.getCreatorEmail(), "event-pending-approval", "mail.eventPendingApproval.subject",
                    event, locale, MailFormats.displayName(creatorName, event.getCreatorEmail()), variables -> { });
        });
    }

    // ---- A quien se anoto ----

    @Async
    @Override
    public void notifyRegistrationConfirmed(final Event event, final Registration registration) {
        // El CTA manda a "Mis inscripciones", no al evento: quien se anoto ya esta viendo estos datos en este mismo
        // mail, lo util es llevarlo a donde puede gestionar (o mas adelante reseñar) sus inscripciones.
        toRegistrant(event, registration, "registration-confirmed", "mail.registrationConfirmed.subject",
                "Fallo la notificacion de inscripcion confirmada", links.registrations(), "registrationsUrl");
    }

    @Async
    @Override
    public void notifyRegistrationPending(final Event event, final Registration registration) {
        toRegistrant(event, registration, "registration-pending", "mail.registrationPending.subject",
                "Fallo la notificacion de inscripcion pendiente", links.registrations(), "registrationsUrl");
    }

    @Async
    @Override
    public void notifyRegistrationApproved(final Event event, final Registration registration) {
        toRegistrant(event, registration, "registration-approved", "mail.registrationApproved.subject",
                "Fallo la notificacion de inscripcion aprobada", links.registrations(), "registrationsUrl");
    }

    @Async
    @Override
    public void notifyRegistrationRejected(final Event event, final Registration registration) {
        toRegistrant(event, registration, "registration-rejected", "mail.registrationRejected.subject",
                "Fallo la notificacion de inscripcion rechazada", links.events(), "eventsUrl");
    }

    @Async
    @Override
    public void notifyWaitlistJoined(final Event event, final Registration registration) {
        // Mismo CTA que el mail de inscripcion confirmada: desde "Mis inscripciones" puede salirse de la lista si ya
        // no le interesa.
        toRegistrant(event, registration, "waitlist-joined", "mail.waitlistJoined.subject",
                "Fallo la notificacion de lista de espera", links.registrations(), "registrationsUrl");
    }

    @Async
    @Override
    public void notifyWaitlistPromoted(final Event event, final Registration registration) {
        toRegistrant(event, registration, "waitlist-promoted", "mail.waitlistPromoted.subject",
                "Fallo el aviso de lugar de la lista de espera", links.registrations(), "registrationsUrl");
    }

    @Async
    @Override
    public void notifyRegistrationRevoked(final Event event, final Registration registration) {
        toRegistrant(event, registration, "registration-revoked", "mail.registrationRevoked.subject",
                "Fallo el aviso de lugar revocado", links.events(), "eventsUrl");
    }

    // ---- A quien organiza el evento ----

    @Async
    @Override
    public void notifyNewRegistration(final Event event, final Registration registration) {
        toCreator(event, "new-registration", "mail.newRegistration.subject",
                "Fallo la notificacion de inscripcion nueva", variables -> putRegistrant(variables, registration));
    }

    @Async
    @Override
    public void notifyNewPendingRegistration(final Event event, final Registration registration) {
        // Manda directo al evento: ahi vive la pantalla de aceptar/rechazar (ver ProfileController), no hace falta un
        // link aparte.
        toCreator(event, "new-pending-registration", "mail.newPendingRegistration.subject",
                "Fallo el aviso de inscripcion pendiente al creador", variables -> {
                    putRegistrant(variables, registration);
                    variables.put("reviewUrl", links.pendingRequests(event.getId()));
                });
    }

    @Async
    @Override
    public void notifyNewWaitlistEntry(final Event event, final Registration registration) {
        toCreator(event, "waitlist-new-entry", "mail.waitlistNewEntry.subject",
                "Fallo el aviso de lista de espera al creador", variables -> putRegistrant(variables, registration));
    }

    @Async
    @Override
    public void notifyRegistrationCancelled(final Event event, final Registration registration,
                                             final boolean offeredToWaitlist) {
        toCreator(event, "registration-cancelled", "mail.registrationCancelled.subject",
                "Fallo el aviso de baja al creador", variables -> {
                    putRegistrant(variables, registration);
                    // La plantilla elige entre dos parrafos con este booleano, asi los dos textos siguen viviendo en el
                    // archivo de mensajes y no en el codigo.
                    variables.put("offeredToWaitlist", offeredToWaitlist);
                });
    }

    @Async
    @Override
    public void notifyEventFull(final Event event) {
        toCreator(event, "event-full", "mail.eventFull.subject", "Fallo el aviso de evento completo", variables -> { });
    }

    @Async
    @Override
    public void notifyOrganizerEventReminder(final Event event) {
        toCreator(event, "event-reminder-organizer", "mail.organizerReminder.subject",
                "Fallo el recordatorio de 24 hs al organizador",
                variables -> variables.put("attendeesUrl", links.attendees(event.getId())));
    }

    @Async
    @Override
    public void notifyNewQuestion(final Event event, final EventQuestion question,
                                  final String askerName, final String askerEmail) {
        toCreator(event, "new-question", "mail.newQuestion.subject", "Fallo el aviso de pregunta nueva", variables -> {
            variables.put("askerName", MailFormats.displayName(askerName, askerEmail));
            variables.put("questionText", question.getText());
            // Directo a la sección de preguntas del detalle.
            variables.put("eventUrl", links.eventQuestions(event.getId()));
        });
    }

    @Async
    @Override
    public void notifyEventRemovedByAdmin(final Event event, final String reason) {
        toCreator(event, "event-removed-by-admin", "mail.eventRemovedByAdmin.subject",
                "Fallo el aviso de evento removido por un admin", variables -> {
                    variables.put("reason", reason);
                    variables.put("eventsUrl", links.events());
                });
    }

    @Async
    @Override
    public void notifyEventApproved(final Event event) {
        toCreator(event, "event-approved", "mail.eventApproved.subject", "Fallo el aviso de evento aprobado",
                variables -> { });
    }

    @Async
    @Override
    public void notifyEventRejected(final Event event, final String reason) {
        toCreator(event, "event-rejected", "mail.eventRejected.subject", "Fallo el aviso de evento rechazado",
                variables -> {
                    variables.put("reason", reason);
                    // El evento rechazado nunca llega a ser accesible: el CTA va al listado general, no a eventUrl
                    // (mismo criterio que notifyEventRemovedByAdmin).
                    variables.put("eventsUrl", links.events());
                });
    }

    @Async
    @Override
    public void notifyEventChangesRequested(final Event event, final String message) {
        toCreator(event, "event-changes-requested", "mail.eventChangesRequested.subject",
                "Fallo el aviso de cambios pedidos por un admin", variables -> {
                    variables.put("message", message);
                    // Al editar directo, no al detalle: es lo que la persona tiene que hacer.
                    variables.put("editEventUrl", links.editEvent(event.getId()));
                });
    }

    // ---- A otras personas ----

    @Async
    @Override
    public void notifyEventInvitation(final Event event, final EventInvitation invitation) {
        attempt("Fallo la invitacion al evento", event, () -> {
            // invitation.getUserId() es optimista (ver el modelo): si es null todavia no tiene cuenta, y entonces se
            // usa el idioma de quien lo invita.
            final Recipient invitee = inviteeOf(event, invitation);
            // Si tiene cuenta, el nombre sale de la misma lectura que el idioma; si no, solo se conoce el mail.
            sendEventMail(invitation.getEmail(), "event-invitation", "mail.eventInvitation.subject", event,
                    invitee.locale(), MailFormats.displayName(invitee.name(), invitation.getEmail()), variables -> { });
        });
    }

    @Async
    @Override
    public void notifyQuestionAnswered(final Event event, final EventQuestion question,
                                       final String askerName, final String askerEmail) {
        attempt("Fallo el aviso de pregunta respondida", event, () ->
                sendEventMail(askerEmail, "question-answered", "mail.questionAnswered.subject", event,
                        localeFor(question.getUser()), MailFormats.displayName(askerName, askerEmail), variables -> {
                            variables.put("questionText", question.getText());
                            variables.put("answerText", question.getAnswer());
                            variables.put("eventUrl", links.eventQuestions(event.getId()));
                        }));
    }

    @Async
    @Override
    public void notifySavedSearchMatches(final User user, final long savedSearchId, final Event example,
                                         final long newEventsCount) {
        try {
            sendMail(user.getEmail(), "saved-search-matches", "mail.savedSearchMatches.subject",
                    new Object[]{ newEventsCount }, example, localeFor(user), MailFormats.displayName(user.getName(), user.getEmail()),
                    variables -> {
                        variables.put("newEventsCount", newEventsCount);
                        variables.put("unsubscribeUrl", links.unsubscribeSavedSearch(savedSearchId));
                    });
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de busqueda guardada, usuario {}", user.getId(), e);
        }
    }

    // ---- Tandas (sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del
    // commit) ----

    @Override
    public void notifyRegistrationPending(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "registration-pending", "mail.registrationPending.subject",
                variables -> variables.put("registrationsUrl", links.registrations()),
                "Fallo la notificacion de inscripcion pendiente");
    }

    @Override
    public void notifyWaitlistPromoted(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "waitlist-promoted", "mail.waitlistPromoted.subject",
                variables -> variables.put("registrationsUrl", links.registrations()),
                "Fallo el aviso de lugar de la lista de espera");
    }

    @Override
    public void notifyWaitlistJoined(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "waitlist-joined", "mail.waitlistJoined.subject",
                variables -> variables.put("registrationsUrl", links.registrations()),
                "Fallo la notificacion de lista de espera");
    }

    @Override
    public void notifyEventCancelled(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "event-cancelled", "mail.eventCancelled.subject",
                variables -> variables.put("eventsUrl", links.events()), "Fallo el aviso de evento cancelado");
    }

    @Override
    public void notifyEventUpdated(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "event-updated", "mail.eventUpdated.subject", variables -> { },
                "Fallo el aviso de evento editado");
    }

    @Override
    public void notifyEventReminder(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "event-reminder", "mail.eventReminder.subject", variables -> { },
                "Fallo el recordatorio de 24 hs");
    }

    // Un mail al organizador por cada pedido de la lista, pero el organizador (nombre e idioma) se busca UNA sola
    // vez y no una por pedido. Si el de uno falla, se sigue con los demas.
    @Override
    public void notifyNewPendingRegistration(final Event event, final List<Registration> registrations) {
        if (registrations.isEmpty()) {
            return;
        }
        final Locale locale;
        final String creatorName;
        try {
            final Recipient creator = creatorOf(event);
            locale = creator.locale();
            creatorName = MailFormats.displayName(creator.name(), event.getCreatorEmail());
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de inscripcion pendiente al creador, evento {}", event.getId(), e);
            return;
        }
        for (final Registration registration : registrations) {
            try {
                sendEventMail(event.getCreatorEmail(), "new-pending-registration",
                        "mail.newPendingRegistration.subject", event, locale, creatorName, variables -> {
                            putRegistrant(variables, registration);
                            variables.put("reviewUrl", links.pendingRequests(event.getId()));
                        });
            } catch (final RuntimeException e) {
                LOGGER.error("Fallo el aviso de inscripcion pendiente al creador, evento {}, inscripcion {}",
                        event.getId(), registration.getId(), e);
            }
        }
    }

    @Override
    public void notifyFollowedUserNewEvent(final List<FollowerContact> followers, final Event event,
                                           final String organizerName) {
        // Una tanda de seguidores (ver MassNotifier, que ya corre en segundo plano). El idioma ya viene en la
        // proyeccion: no se vuelve a la base por cada seguidor. Si el mail de uno falla, se sigue con los demas.
        for (final FollowerContact follower : followers) {
            try {
                sendMail(follower.getEmail(), "followed-user-new-event", "mail.followedUserNewEvent.subject",
                        new Object[]{ organizerName }, event, toLocale(follower.getLocale()),
                        MailFormats.displayName(follower.getName(), follower.getEmail()),
                        variables -> variables.put("organizerName", organizerName));
            } catch (final RuntimeException e) {
                LOGGER.error("Fallo el aviso de evento nuevo de un organizador seguido, usuario {}", follower.getId(), e);
            }
        }
    }

    // ---- Lo que comparten todos ----

    // Un mail a UNA persona que se anoto: su mail y su nombre salen de la inscripcion, el idioma de su cuenta.
    // extraName/extraValue: el unico dato propio de ese mail (el link al que lleva).
    private void toRegistrant(final Event event, final Registration registration, final String template,
                              final String subjectKey, final String errorMessage, final String extraValue,
                              final String extraName) {
        attempt(errorMessage, event, () -> {
            final Locale locale = localeForUserId(registration.getUserId());
            sendEventMail(registration.getEmail(), template, subjectKey, event, locale,
                    MailFormats.displayName(registration.getName(), registration.getEmail()),
                    variables -> variables.put(extraName, extraValue));
        });
    }

    // Un mail a quien organiza el evento: su nombre e idioma salen de su cuenta (se leen una sola vez).
    private void toCreator(final Event event, final String template, final String subjectKey,
                           final String errorMessage, final Consumer<Map<String, Object>> extraVariables) {
        attempt(errorMessage, event, () -> {
            final Recipient creator = creatorOf(event);
            sendEventMail(event.getCreatorEmail(), template, subjectKey, event, creator.locale(),
                    MailFormats.displayName(creator.name(), event.getCreatorEmail()), extraVariables);
        });
    }

    // Quien se anoto, como lo ve el organizador en el mail.
    private void putRegistrant(final Map<String, Object> variables, final Registration registration) {
        variables.put("registrantName", MailFormats.displayName(registration.getName(), registration.getEmail()));
        variables.put("registrantEmail", registration.getEmail());
    }

    // El armado y el envio de UN mail pueden fallar (la base, la plantilla, el servidor de correo): son avisos en
    // segundo plano y no hay nadie esperando, asi que se registra y no se propaga.
    private void attempt(final String errorMessage, final Event event, final Runnable sending) {
        try {
            sending.run();
        } catch (final RuntimeException e) {
            LOGGER.error("{}, evento {}", errorMessage, event.getId(), e);
        }
    }

    // El mail de un evento: el asunto lleva el titulo, y las variables son las del evento mas el nombre de quien lo
    // recibe y los datos propios de ese mail.
    private void sendEventMail(final String to, final String template, final String subjectKey, final Event event,
                               final Locale locale, final String personName,
                               final Consumer<Map<String, Object>> extraVariables) {
        sendMail(to, template, subjectKey, new Object[]{ event.getTitle() }, event, locale, personName,
                extraVariables);
    }

    private void sendMail(final String to, final String template, final String subjectKey, final Object[] subjectArgs,
                          final Event event, final Locale locale, final String personName,
                          final Consumer<Map<String, Object>> extraVariables) {
        final Map<String, Object> variables = eventVariables(event, locale);
        variables.put("personName", personName);
        extraVariables.accept(variables);
        mailService.send(to, template, subjectKey, subjectArgs, variables, locale);
    }

    /**
     * Un mismo mail a cada inscripto de la tanda, en el hilo de MassNotifier (que ya corre en segundo plano y
     * despues del commit): no se abre otra tarea asincronica por tanda ni por persona. Los idiomas de toda la tanda
     * se buscan con UNA consulta (findByIds) en vez de una por inscripto. Si el mail de uno falla se registra y se
     * sigue con los demas.
     */
    private void notifyRegistrants(final Event event, final List<Registration> registrations,
                                   final String template, final String subjectKey,
                                   final Consumer<Map<String, Object>> extraVariables, final String errorMessage) {
        if (registrations.isEmpty()) {
            return;
        }
        final Map<Long, User> users;
        try {
            users = usersById(registrations);
        } catch (final RuntimeException e) {
            LOGGER.error("{}: no se pudieron buscar los destinatarios, evento {}", errorMessage, event.getId(), e);
            return;
        }
        for (final Registration registration : registrations) {
            try {
                final User user = registration.getUserId() == null ? null : users.get(registration.getUserId());
                // Sin cuenta o sin fila: idioma por defecto, igual que localeForUserId().
                final Locale locale = user == null ? DEFAULT_LOCALE : localeFor(user);
                sendEventMail(registration.getEmail(), template, subjectKey, event, locale,
                        MailFormats.displayName(registration.getName(), registration.getEmail()), extraVariables);
            } catch (final RuntimeException e) {
                LOGGER.error("{}, evento {}, inscripcion {}", errorMessage, event.getId(), registration.getId(), e);
            }
        }
    }

    // Las cuentas de esos inscriptos, por id, con una sola consulta.
    private Map<Long, User> usersById(final List<Registration> registrations) {
        final List<Long> ids = registrations.stream()
                .map(Registration::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, User> byId = new HashMap<>();
        for (final User user : userService.findByIds(ids)) {
            byId.put(user.getId(), user);
        }
        return byId;
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
        variables.put("eventDate", MailFormats.date(event, locale));
        variables.put("eventPlace", MailFormats.place(event));
        variables.put("eventPrice", MailFormats.price(event, locale, messageSource));
        // El link tiene que ser absoluto: el mail se abre en Gmail, no en la app.
        variables.put("eventUrl", links.event(event.getId()));
        return variables;
    }


    /**
     * El idioma guardado de un destinatario que YA tenemos cargado (no hace
     * falta ir a buscarlo por id).
     */
    private Locale localeFor(final User user) {
        return toLocale(user.getLocale());
    }

    /**
     * Nombre e idioma de un destinatario, leidos de UNA sola vez (una sola consulta
     * al usuario) en vez de una para el idioma y otra para el nombre.
     */
    private record Recipient(String name, Locale locale) {
    }

    /**
     * Igual que localeFor(User), pero a partir de un id que puede no
     * corresponder a nadie con cuenta todavia: userId null (invitado sin
     * cuenta, ver EventInvitation.userId) o sin fila (no deberia pasar, pero
     * no vale la pena reventar un mail por eso) caen los dos en DEFAULT_LOCALE
     * y sin nombre (MailFormats.displayName() sabe armar uno a partir del mail).
     */
    private Recipient recipientOf(final Long userId) {
        if (userId == null) {
            return new Recipient(null, DEFAULT_LOCALE);
        }
        return userService.findById(userId)
                .map(u -> new Recipient(u.getName(), localeFor(u)))
                .orElse(new Recipient(null, DEFAULT_LOCALE));
    }

    private Locale localeForUserId(final Long userId) {
        return recipientOf(userId).locale();
    }

    /** El creador del evento: destinatario de los avisos sobre su evento. */
    private Recipient creatorOf(final Event event) {
        return recipientOf(event.getCreatorId());
    }

    /**
     * El destinatario del mail de invitacion a un evento privado.
     *
     * Si el invitado ya tiene cuenta, su nombre y su idioma. Si todavia no (invitation.getUserId() es
     * null), no hay preferencia que buscar: se usa el idioma del creador, que es la mejor
     * pista que hay para alguien que no conocemos (y sin nombre). Si tiene cuenta pero nunca eligio
     * idioma, queda en el default (esa persona no tiene preferencia, no le falta cuenta).
     */
    private Recipient inviteeOf(final Event event, final EventInvitation invitation) {
        if (invitation.getUserId() != null) {
            return recipientOf(invitation.getUserId());
        }
        return new Recipient(null, creatorOf(event).locale());
    }

    private Locale toLocale(final String languageTag) {
        return (languageTag == null || languageTag.isEmpty()) ? DEFAULT_LOCALE : new Locale(languageTag);
    }
}
