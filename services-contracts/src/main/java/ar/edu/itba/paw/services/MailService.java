package ar.edu.itba.paw.services;

import java.util.Locale;
import java.util.Map;

public interface MailService {

    /**
     * Manda un mail con el HTML ya armado. Es el metodo "crudo": no sabe nada
     * de plantillas ni de idiomas, solo despacha.
     *
     * @param to       destinatario
     * @param subject  asunto
     * @param htmlBody cuerpo del mail en HTML
     */
    void send(String to, String subject, String htmlBody);

    /**
     * Manda un mail armado a partir de una plantilla Thymeleaf. Es el que usa
     * la aplicacion: arma el HTML y busca el asunto en el idioma que corresponda,
     * y despues delega en el metodo de arriba.
     *
     * @param to          destinatario
     * @param template    nombre de la plantilla, SIN carpeta ni extension.
     *                    "event-created" busca mails/event-created.html
     * @param subjectKey  clave del asunto en el archivo de textos
     *                    (ej: mail.eventCreated.subject)
     * @param subjectArgs valores que reemplazan los {0}, {1}... del asunto
     * @param variables   datos que la plantilla usa con ${...}
     * @param locale      idioma del mail
     */
    void send(String to,
              String template,
              String subjectKey,
              Object[] subjectArgs,
              Map<String, Object> variables,
              Locale locale);
}
