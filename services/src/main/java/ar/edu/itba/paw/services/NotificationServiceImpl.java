package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
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
