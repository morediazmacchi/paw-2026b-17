package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import org.springframework.context.MessageSource;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Como se escriben en los mails los datos que se repiten en casi todos (a quien se saluda, la fecha, el lugar y el
 * precio de un evento). Funciones puras: lo mismo de entrada da lo mismo de salida, sin consultar nada.
 */
final class MailFormats {

    private MailFormats() {
    }

    /**
     * Con que nombre saludamos: el que nos pasan; si no hay, lo que va antes del arroba del mail (es mejor "Hola sol,"
     * que "Hola null,"). No consulta nada: esto corre una vez por destinatario dentro de los avisos masivos, y una
     * consulta por cada uno seria un N+1.
     */
    static String displayName(final String name, final String email) {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        if (email == null) {
            return "";
        }
        final int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    // "sabado, 12 de septiembre de 2026, 21:00": FULL para la fecha y SHORT para la hora; el formato lo elige Java segun
    // el idioma. "" si el evento no tiene fecha.
    static String date(final Event event, final Locale locale) {
        if (event.getEventDate() == null) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT, locale).format(event.getEventDate());
    }

    // La direccion y, si se eligio, el barrio.
    static String place(final Event event) {
        if (event.getNeighborhood() == null) {
            return event.getAddress();
        }
        return event.getAddress() + ", " + event.getNeighborhood().getDisplayName();
    }

    // "Gratis" / "Free" sale del archivo de textos, como todo lo demas. Igual que la web (paw:price): el numero lo
    // escribe cada idioma (1.500 / 1,500), sin centavos, y donde va el simbolo lo dice el archivo de textos.
    static String price(final Event event, final Locale locale, final MessageSource messageSource) {
        if (event.isFree()) {
            return messageSource.getMessage("mail.event.free", null, locale);
        }
        final NumberFormat number = NumberFormat.getNumberInstance(locale);
        number.setMaximumFractionDigits(0);
        return messageSource.getMessage("mail.event.priceFormat",
                new Object[]{ number.format(event.getPrice()) }, locale);
    }
}
