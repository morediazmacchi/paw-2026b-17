package ar.edu.itba.paw.webapp.mail;

import ar.edu.itba.paw.services.MailLinks;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Arma los links de los mails con las rutas reales de la web (los @RequestMapping de los controllers) y el prefijo del
 * servidor. Es el UNICO lugar que conoce esas rutas para los mails: NotificationServiceImpl solo pide cada link por lo
 * que es (MailLinks).
 */
public class WebMailLinks implements MailLinks {

    // La parte fija de los links (esquema + servidor + contexto). Sale de mail.properties (app.base-url) porque cambia
    // entre la maquina de desarrollo y pampero.
    private final String baseUrl;

    public WebMailLinks(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String registrations() {
        return baseUrl + "/perfil/inscripciones";
    }

    @Override
    public String events() {
        return baseUrl + "/eventos";
    }

    @Override
    public String event(final long eventId) {
        return baseUrl + "/eventos/" + eventId;
    }

    @Override
    public String eventQuestions(final long eventId) {
        return event(eventId) + "#preguntas";
    }

    @Override
    public String editEvent(final long eventId) {
        return event(eventId) + "/editar";
    }

    @Override
    public String attendees(final long eventId) {
        return event(eventId) + "/inscriptos";
    }

    @Override
    public String pendingRequests(final long eventId) {
        return baseUrl + "/perfil/eventos/" + eventId + "/inscriptos";
    }

    @Override
    public String unsubscribeSavedSearch(final long savedSearchId) {
        return baseUrl + "/perfil/listas/" + savedSearchId + "/desuscribirse";
    }

    @Override
    public String login() {
        return baseUrl + "/login";
    }

    @Override
    public String confirmAccount(final String token, final String returnTo) {
        final String redirectParam = returnTo == null ? ""
                : "&redirect=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
        return baseUrl + "/confirmar-cuenta?token=" + token + redirectParam;
    }

    @Override
    public String resetPassword(final String token) {
        return baseUrl + "/login/restablecer?token=" + token;
    }

    @Override
    public String forgotPassword() {
        return baseUrl + "/login/recuperar";
    }
}
