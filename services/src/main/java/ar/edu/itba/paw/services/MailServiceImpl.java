package ar.edu.itba.paw.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;

    // Remitente de los mails (mail.properties). Por constructor y no en el campo: asi un test
    // lo pasa como cualquier otro parametro, sin tocar internos de la clase.
    private final String from;

    @Autowired
    public MailServiceImpl(final JavaMailSender mailSender,
                           final TemplateEngine templateEngine,
                           @Qualifier("mailMessageSource") final MessageSource messageSource,
                           @Value("${mail.from}") final String from) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.from = from;
    }

    /**
     * El metodo crudo: recibe el HTML ya listo y lo despacha.
     * Es el unico lugar de toda la clase que habla con el servidor de mail.
     */
    @Override
    public boolean send(final String to, final String subject, final String htmlBody) {
        try {
            // MimeMessage es el mail "completo" (permite HTML, adjuntos, acentos).
            // El otro camino, SimpleMailMessage, solo permite texto plano.
            final MimeMessage message = mailSender.createMimeMessage();

            // MimeMessageHelper es un envoltorio para no pelearse con la API cruda
            // de javax.mail. El "false" es multipart: no mandamos adjuntos.
            final MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            // El "true" del final le dice al cliente de mail "esto es HTML,
            // renderizalo", en vez de mostrar las etiquetas.
            helper.setText(htmlBody, true);

            mailSender.send(message);
            LOGGER.info("Mail enviado a {} con asunto '{}'", to, subject);
            return true;

        } catch (final Exception e) {
            // A proposito NO relanzamos: si el servidor de mail esta caido, el
            // usuario igual tiene que poder crear su evento o anotarse. El mail
            // es un efecto secundario, no la operacion principal.
            LOGGER.error("No se pudo enviar el mail a {} con asunto '{}'", to, subject, e);
            return false;
        }
    }

    /**
     * El metodo con plantilla: arma el HTML y el asunto en el idioma pedido,
     * y despues delega en el de arriba.
     */
    @Override
    public void send(final String to,
                     final String template,
                     final String subjectKey,
                     final Object[] subjectArgs,
                     final Map<String, Object> variables,
                     final Locale locale) {
        // 1) El Context es la bolsa de datos que ve la plantilla.
        //    Lleva tambien el idioma: es lo que hace que el mismo archivo
        //    HTML salga en castellano o en ingles.
        final Context context = new Context(locale, variables);

        // 2) Thymeleaf junta la plantilla con los datos y devuelve el HTML final.
        final String htmlBody = templateEngine.process(template, context);

        // 3) El asunto NO esta en la plantilla (un asunto no es HTML),
        //    asi que lo pedimos aparte al mismo archivo de textos.
        final String subject = messageSource.getMessage(subjectKey, subjectArgs, locale);

        LOGGER.debug("Mail '{}' armado para {} en {}", template, to, locale);
        send(to, subject, htmlBody);
    }
}
