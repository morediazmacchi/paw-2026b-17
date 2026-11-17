// Las dos pestañas del perfil publico: los eventos que el organizador todavia tiene por delante y los que ya hizo. Se
// ve una sola a la vez (UserProfileService.findProfile() carga solo la pagina de la que se muestra).
package ar.edu.itba.paw.model;

public enum ProfileTab {
    // Eventos publicos que todavia no pasaron.
    UPCOMING,
    // Eventos publicos que ya pasaron ("realizados").
    PAST;

    // La pestaña que pide la URL (?tab=): ninguna, o una que no existe, da null y el service elige la de por defecto
    // (la de proximos, salvo que no tenga y si tenga realizados).
    public static ProfileTab fromParam(final String value) {
        for (final ProfileTab tab : values()) {
            if (tab.name().equals(value)) {
                return tab;
            }
        }
        return null;
    }
}
