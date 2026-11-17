package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.model.SupportedLocale;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Locale;

/**
 * El idioma de la pagina: la cookie manda (la escribe el boton de idioma); si la persona todavia no
 * eligio ninguno, se usa el que pide el navegador (header Accept-Language) siempre que sea uno de los
 * soportados ({@link SupportedLocale}). Si no pide ninguno soportado, o no manda el header, queda espanol.
 *
 * Sin esta clase, el "idioma por defecto" fijo ignoraba el navegador: alguien con el navegador en ingles
 * veia siempre la pagina en espanol hasta apretar el boton.
 */
public class AcceptLanguageCookieLocaleResolver extends CookieLocaleResolver {

    private static final Locale FALLBACK = new Locale(SupportedLocale.ES.getCode());

    @Override
    protected Locale determineDefaultLocale(final HttpServletRequest request) {
        // Sin header, request.getLocales() devuelve el idioma del SERVIDOR (en produccion puede ser
        // ingles): no es una preferencia de la persona, asi que no se mira.
        if (request.getHeader("Accept-Language") == null) {
            return FALLBACK;
        }
        // getLocales() ya viene ordenado por el q= del header (el preferido primero).
        final Enumeration<Locale> requested = request.getLocales();
        while (requested.hasMoreElements()) {
            final Locale locale = requested.nextElement();
            if (SupportedLocale.fromCode(locale.getLanguage()).isPresent()) {
                return new Locale(locale.getLanguage());
            }
        }
        return FALLBACK;
    }
}
