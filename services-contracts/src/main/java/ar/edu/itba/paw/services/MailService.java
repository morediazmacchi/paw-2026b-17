package ar.edu.itba.paw.services;

public interface MailService {

    /**
     * Manda un mail.
     *
     * @param to       destinatario
     * @param subject  asunto
     * @param htmlBody cuerpo del mail en HTML
     */
    void send(String to, String subject, String htmlBody);
}
