package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    // Por ahora todos los mails salen en castellano. Cuando el sitio tenga
    // seleccion de idioma, esto sale de la preferencia del usuario.
    private static final Locale DEFAULT_LOCALE = new Locale("es");

    // Los precios son siempre en pesos argentinos, no dependen del idioma del mail.
    private static final Locale MONEY_LOCALE = new Locale("es", "AR");

    private final MailService mailService;
    private final MessageSource messageSource;

    // De donde sale la parte fija de los links. Vive en mail.properties porque
    // cambia entre tu maquina y pampero.
    @Value("${app.base-url}")
    private String baseUrl;

    @Autowired
    public NotificationServiceImpl(final MailService mailService,
                                   @Qualifier("mailMessageSource") final MessageSource messageSource) {
        this.mailService = mailService;
        this.messageSource = messageSource;
    }

    @Async
    @Override
    public void notifyEventCreated(final Event event, final String creatorName) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(creatorName, event.getCreatorEmail()));

            mailService.send(event.getCreatorEmail(),
                    "event-created",
                    "mail.eventCreated.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            // Estamos en otro hilo: si no lo atrapamos aca, la excepcion no
            // aparece en ningun lado. Nadie esta esperando este metodo.
            LOGGER.error("Fallo la notificacion de evento creado, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationConfirmed(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            // El CTA manda a "Mis inscripciones", no al evento: quien se anoto ya
            // esta viendo estos datos en este mismo mail, lo util es llevarlo a
            // donde puede gestionar (o mas adelante reseñar) sus inscripciones.
            variables.put("registrationsUrl", baseUrl + "/perfil/inscripciones");

            mailService.send(registration.getEmail(),
                    "registration-confirmed",
                    "mail.registrationConfirmed.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion confirmada, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyNewRegistration(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());

            mailService.send(event.getCreatorEmail(),
                    "new-registration",
                    "mail.newRegistration.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion nueva, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationPending(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrationsUrl", baseUrl + "/perfil/inscripciones");

            mailService.send(registration.getEmail(),
                    "registration-pending",
                    "mail.registrationPending.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion pendiente, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyNewPendingRegistration(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());
            // Manda directo al evento: ahi vive la pantalla de aceptar/rechazar
            // (ver ProfileController), no hace falta un link aparte.
            variables.put("reviewUrl", baseUrl + "/perfil/eventos/" + event.getId() + "/inscriptos");

            mailService.send(event.getCreatorEmail(),
                    "new-pending-registration",
                    "mail.newPendingRegistration.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de inscripcion pendiente al creador, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationApproved(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrationsUrl", baseUrl + "/perfil/inscripciones");

            mailService.send(registration.getEmail(),
                    "registration-approved",
                    "mail.registrationApproved.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion aprobada, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationRejected(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("eventsUrl", baseUrl + "/eventos");

            mailService.send(registration.getEmail(),
                    "registration-rejected",
                    "mail.registrationRejected.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de inscripcion rechazada, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventInvitation(final Event event, final EventInvitation invitation) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            // No hay nombre: a esta altura solo se conoce el mail al que se invito.
            variables.put("personName", displayName(null, invitation.getEmail()));
            // Pisa el eventUrl que ya puso eventVariables(): este SI lleva el
            // token, es lo que le da acceso a un evento que de otra forma le
            // daria 404 (ver EventController.canView).
            variables.put("eventUrl", baseUrl + "/eventos/" + event.getId() + "?invitacion=" + invitation.getToken());

            mailService.send(invitation.getEmail(),
                    "event-invitation",
                    "mail.eventInvitation.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la invitacion al evento, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyWaitlistJoined(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            // Mismo CTA que el mail de inscripcion confirmada: desde "Mis
            // inscripciones" puede salirse de la lista si ya no le interesa.
            variables.put("registrationsUrl", baseUrl + "/perfil/inscripciones");

            mailService.send(registration.getEmail(),
                    "waitlist-joined",
                    "mail.waitlistJoined.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la notificacion de lista de espera, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyNewWaitlistEntry(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(null, event.getCreatorEmail()));
            variables.put("registrantName", displayName(registration.getName(), registration.getEmail()));
            variables.put("registrantEmail", registration.getEmail());

            mailService.send(event.getCreatorEmail(),
                    "waitlist-new-entry",
                    "mail.waitlistNewEntry.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de lista de espera al creador, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyWaitlistInvitation(final Event event, final Registration registration,
                                          final String token, final java.util.Date expiresAt) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            // El link NO acepta por si solo: abre una pagina con dos botones. Un
            // GET que cambiara estado se ejecutaria solo cuando el antivirus del
            // correo abre los links para escanearlos.
            variables.put("invitationUrl", baseUrl + "/invitacion/" + token);
            variables.put("deadline", DateFormat
                    .getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, DEFAULT_LOCALE)
                    .format(expiresAt));

            mailService.send(registration.getEmail(),
                    "waitlist-invitation",
                    "mail.waitlistInvitation.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo la invitacion de lista de espera, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventFull(final Event event) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(null, event.getCreatorEmail()));

            mailService.send(event.getCreatorEmail(),
                    "event-full",
                    "mail.eventFull.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento completo, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyRegistrationCancelled(final Event event, final Registration registration,
                                             final boolean offeredToWaitlist) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
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
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de baja al creador, evento {}", event.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyAccountVerification(final User user, final String token) {
        try {
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            variables.put("confirmUrl", baseUrl + "/confirmar-cuenta?token=" + token);

            mailService.send(user.getEmail(),
                    "account-verification",
                    "mail.accountVerification.subject",
                    null,
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el mail de confirmacion de cuenta, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyPasswordReset(final User user, final String token) {
        try {
            final Map<String, Object> variables = new HashMap<>();
            variables.put("personName", displayName(user.getName(), user.getEmail()));
            variables.put("resetUrl", baseUrl + "/login/restablecer?token=" + token);

            mailService.send(user.getEmail(),
                    "password-reset",
                    "mail.passwordReset.subject",
                    null,
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el mail de restablecer contraseña, usuario {}", user.getId(), e);
        }
    }

    @Async
    @Override
    public void notifyEventCancelled(final Event event, final Registration registration) {
        try {
            final Map<String, Object> variables = eventVariables(event, DEFAULT_LOCALE);
            variables.put("personName", displayName(registration.getName(), registration.getEmail()));
            variables.put("eventsUrl", baseUrl + "/eventos");

            mailService.send(registration.getEmail(),
                    "event-cancelled",
                    "mail.eventCancelled.subject",
                    new Object[]{ event.getTitle() },
                    variables,
                    DEFAULT_LOCALE);
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo el aviso de evento cancelado, evento {}", event.getId(), e);
        }
    }

    /**
     * Los datos del evento que usan las tres plantillas. Se arma una sola vez
     * aca en vez de repetirlo en cada metodo.
     */
    private Map<String, Object> eventVariables(final Event event, final Locale locale) {
        final Map<String, Object> variables = new HashMap<>();
        variables.put("eventTitle", event.getTitle());
        variables.put("eventDate", formatDate(event, locale));
        variables.put("eventPlace", formatPlace(event));
        variables.put("eventPrice", formatPrice(event, locale));
        // El link tiene que ser absoluto: el mail se abre en Gmail, no en la app.
        variables.put("eventUrl", baseUrl + "/eventos/" + event.getId());
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
     * Con que nombre saludamos. Si no lo sabemos, usamos lo que va antes del
     * arroba: es mejor "Hola sol," que "Hola null,".
     */
    private String displayName(final String name, final String email) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        if (email == null) {
            return "";
        }
        final int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
