package ar.edu.itba.paw.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los unicos idiomas de la app ("es", "en"). Una sola lista: la comparten el controller (cookie), el
 * resolver de idioma (Accept-Language) y UserService.updateLocale (columna preferred_locale), asi
 * no se puede guardar ni elegir otro idioma.
 */
public enum SupportedLocale {
    ES("es"),
    EN("en");

    private final String code;

    SupportedLocale(final String code) {
        this.code = code;
    }

    // El codigo que se guarda en la base y en la cookie: "es" / "en".
    public String getCode() {
        return code;
    }

    // Vacio si el codigo no es uno de los soportados (o es null).
    public static Optional<SupportedLocale> fromCode(final String code) {
        return Arrays.stream(values()).filter(locale -> locale.code.equals(code)).findFirst();
    }
}
