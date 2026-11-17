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

import java.text.DateFormat;
import java.text.NumberFormat;
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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());
            // Manda directo al evento: ahi vive la pantalla de aceptar/rechazar
            // (ver ProfileController), no hace falta un link aparte.
            variables.put("reviewUrl", links.pendingRequests(event.getId()));

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

    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
    @Override
    public void notifyRegistrationPending(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "registration-pending", "mail.registrationPending.subject",
                variables -> variables.put("registrationsUrl", links.registrations()),
                "Fallo la notificacion de inscripcion pendiente");
    }

    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
    @Override
    public void notifyWaitlistPromoted(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "waitlist-promoted", "mail.waitlistPromoted.subject",
                variables -> variables.put("registrationsUrl", links.registrations()),
                "Fallo el aviso de lugar de la lista de espera");
    }

    // Un mail al organizador por cada pedido de la lista, pero el organizador (nombre e idioma) se busca UNA sola
    // vez y no una por pedido. Si el de uno falla, se sigue con los demas.
    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
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
            creatorName = displayName(creator.name(), event.getCreatorEmail());
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de inscripcion pendiente al creador, evento {}", event.getId(), e);
            return;
        }
        for (final Registration registration : registrations) {
            try {
                final Map<String, Object> variables = eventVariables(event, locale);
                variables.put("personName", creatorName);
                variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
                variables.put("registrantEmail", registration.getEmail());
                variables.put("reviewUrl", links.pendingRequests(event.getId()));

                mailService.send(event.getCreatorEmail(),
                        "new-pending-registration",
                        "mail.newPendingRegistration.subject",
                        new Object[]{ event.getTitle() },
                        variables,
                        locale);
            } catch (final RuntimeException e) {
                LOGGER.error("Fallo el aviso de inscripcion pendiente al creador, evento {}, inscripcion {}",
                        event.getId(), registration.getId(), e);
            }
        }
    }

    @Async
    @Override
    public void notifyEventInvitation(final Event event, final EventInvitation invitation) {
        try {
            // invitation.getUserId() es optimista (ver el modelo): si es null todavia
            // no tiene cuenta, y entonces se usa el idioma de quien lo invita.
            final Recipient invitee = inviteeOf(event, invitation);
            final Locale locale = invitee.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            // Si tiene cuenta, el nombre sale de la misma lectura que el idioma; si no, solo se conoce el mail.
            variables.put("personName", displayName(invitee.name(), invitation.getEmail()));

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

    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
    @Override
    public void notifyWaitlistJoined(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "waitlist-joined", "mail.waitlistJoined.subject",
                variables -> variables.put("registrationsUrl", links.registrations()),
                "Fallo la notificacion de lista de espera");
    }

    @Async
    @Override
    public void notifyNewWaitlistEntry(final Event event, final Registration registration) {
        try {
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));

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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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

    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
    @Override
    public void notifyEventCancelled(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "event-cancelled", "mail.eventCancelled.subject", variables -> variables.put("eventsUrl", links.events()), "Fallo el aviso de evento cancelado");
    }

    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
    @Override
    public void notifyEventUpdated(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "event-updated", "mail.eventUpdated.subject", variables -> { }, "Fallo el aviso de evento editado");
    }

    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
    @Override
    public void notifyEventReminder(final Event event, final List<Registration> registrations) {
        notifyRegistrants(event, registrations, "event-reminder", "mail.eventReminder.subject", variables -> { }, "Fallo el recordatorio de 24 hs");
    }

    @Async
    @Override
    public void notifyOrganizerEventReminder(final Event event) {
        try {
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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
            final Locale locale = localeFor(question.getUser());
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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));

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
            final Locale locale = localeFor(user);
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
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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

    // Sin @Async a proposito: la tanda la manda MassNotifier, que ya corre en segundo plano y despues del commit.
    @Override
    public void notifyFollowedUserNewEvent(final List<FollowerContact> followers, final Event event,
                                           final String organizerName) {
        // Una tanda de seguidores (ver MassNotifier, que ya corre en segundo plano). El idioma ya viene en la
        // proyeccion: no se vuelve a la base por cada seguidor. Si el mail de uno falla, se sigue con los demas.
        for (final FollowerContact follower : followers) {
            try {
                final Locale locale = toLocale(follower.getLocale());
                final Map<String, Object> variables = eventVariables(event, locale);
                variables.put("personName", displayName(follower.getName(), follower.getEmail()));
                variables.put("organizerName", organizerName);

                mailService.send(follower.getEmail(),
                        "followed-user-new-event",
                        "mail.followedUserNewEvent.subject",
                        new Object[]{ organizerName },
                        variables,
                        locale);
            } catch (final RuntimeException e) {
                LOGGER.error("Fallo el aviso de evento nuevo de un organizador seguido, usuario {}", follower.getId(), e);
            }
        }
    }

    @Async
    @Override
    public void notifyEventChangesRequested(final Event event, final String message) {
        try {
            final Recipient creator = creatorOf(event);
            final Locale locale = creator.locale();
            final Map<String, Object> variables = eventVariables(event, locale);
            variables.put("personName", displayName(creator.name(), event.getCreatorEmail()));
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
                final Map<String, Object> variables = eventVariables(event, locale);
                variables.put("personName", displayName(registration.getName(), registration.getEmail()));
                extraVariables.accept(variables);

                mailService.send(registration.getEmail(),
                        template,
                        subjectKey,
                        new Object[]{ event.getTitle() },
                        variables,
                        locale);
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
        // Igual que la web (paw:price): el numero lo escribe cada idioma (1.500 / 1,500),
        // sin centavos, y donde va el simbolo lo dice el archivo de textos.
        final NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMaximumFractionDigits(0);
        return messageSource.getMessage("mail.event.priceFormat",
                new Object[]{ number.format(event.getPrice()) }, locale);
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
        final String accountName = userService.findByEmail(email)
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
     * y sin nombre (displayName() sabe armar uno a partir del mail).
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
