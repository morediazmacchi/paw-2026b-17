package ar.edu.itba.paw.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los unicos idiomas de la app. Antes la lista ("es", "en") vivia solo en el controller y nada impedia
 * guardar otro valor como idioma preferido de un usuario: ahora la comparten el controller (cookie) y
 * UserService.updateLocale (columna preferred_locale).
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
