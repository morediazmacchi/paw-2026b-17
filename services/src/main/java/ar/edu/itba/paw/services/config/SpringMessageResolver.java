package ar.edu.itba.paw.services.config;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.messageresolver.IMessageResolver;

/**
 * Puente entre Thymeleaf y el MessageSource de Spring.
 *
 * Thymeleaf trae su propio sistema de textos: busca un archivo .properties
 * con el mismo nombre que la plantilla. Eso nos obligaria a tener un archivo
 * de textos por cada mail, con las frases comunes repetidas en todos.
 *
 * Con esta clase, cuando una plantilla escribe #{mail.eventCreated.title},
 * Thymeleaf nos lo pregunta a nosotros y nosotros se lo pedimos al
 * MessageSource de Spring, que lee un unico archivo por idioma.
 */
public class SpringMessageResolver implements IMessageResolver {

    private final MessageSource messageSource;

    public SpringMessageResolver(final MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public String getName() {
        return "SPRING";
    }

    @Override
    public Integer getOrder() {
        return 0;
    }

    @Override
    public String resolveMessage(final ITemplateContext context, final Class<?> origin,
                                 final String key, final Object[] messageParameters) {
        try {
            // context.getLocale() es el idioma que le pasamos al armar el Context
            // desde MailServiceImpl. Por eso el mismo template sale en es o en en.
            return messageSource.getMessage(key, messageParameters, context.getLocale());
        } catch (final NoSuchMessageException e) {
            // Devolver null significa "no lo encontre". Thymeleaf entonces llama
            // al metodo de abajo. No conviene tirar excepcion: un texto faltante
            // no deberia romper el mail entero.
            return null;
        }
    }

    @Override
    public String createAbsentMessageRepresentation(final ITemplateContext context, final Class<?> origin,
                                                    final String key, final Object[] messageParameters) {
        // Si falta una clave, en vez de reventar aparece ??mail.loQueSea_es?? en
        // el mail. Feo a proposito: se ve al toque cual quedo sin traducir.
        return "??" + key + "_" + context.getLocale() + "??";
    }
}
