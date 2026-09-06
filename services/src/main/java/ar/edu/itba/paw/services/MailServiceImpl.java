package ar.edu.itba.paw.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailServiceImpl.class);

    // El tipo es la interfaz JavaMailSender, no JavaMailSenderImpl.
    // El bean lo arma MailConfig; aca solo lo usamos.
    private final JavaMailSender mailSender;

    // Remitente. Sale del mail.properties para no hardcodearlo.
    @Value("${mail.from}")
    private String from;

    @Autowired
    public MailServiceImpl(final JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(final String to, final String subject, final String htmlBody) {
        try {
            // MimeMessage es el mail "completo" (permite HTML, adjuntos, acentos).
            // El otro camino, SimpleMailMessage, solo permite texto plano.
            final MimeMessage message = mailSender.createMimeMessage();

            // MimeMessageHelper es un envoltorio para no pelearse con la API cruda
            // de javax.mail. El "false" es multipart: no vamos a mandar adjuntos.
            final MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            // El "true" del final es lo que le dice al cliente de mail
            // "esto es HTML, renderizalo", en vez de mostrar las etiquetas.
            helper.setText(htmlBody, true);

            mailSender.send(message);
            LOGGER.info("Mail enviado a {} con asunto '{}'", to, subject);

        } catch (final Exception e) {
            // A proposito NO relanzamos la excepcion.
            // Si el servidor de mail esta caido, el usuario igual tiene que poder
            // crear su evento o anotarse: el mail es un efecto secundario, no la
            // operacion principal. Lo dejamos en el log y seguimos.
            LOGGER.error("No se pudo enviar el mail a {} con asunto '{}'", to, subject, e);
        }
    }
}
