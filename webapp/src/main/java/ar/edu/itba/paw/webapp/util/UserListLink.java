package ar.edu.itba.paw.webapp.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// A donde vuelven las acciones de cada fila de "Usuarios" (suspender, reactivar, promover, sacar admin).
//
// Esas acciones son un POST que responde con un redirect (para que recargar la pagina no repita la accion). Si el
// redirect fuera siempre a "/admin/usuarios", quien estaba mirando una busqueda o la pagina 3 volveria a la lista
// completa. Por eso la pantalla manda en cada formulario un "returnTo" con la direccion tal como se la estaba viendo
// (of()), y el controller vuelve ahi (safeReturnTo()).
public final class UserListLink {

    private static final String PATH = "/admin/usuarios";

    private UserListLink() {
    }

    // La pantalla de usuarios con la busqueda por mail y la pagina que se estan viendo. La primera pagina y el texto
    // vacio no se escriben (queda la URL limpia). El texto se codifica entero (un "+" o un "&" de un mail no se pierde).
    public static String of(final String q, final int page) {
        final StringBuilder link = new StringBuilder(PATH);
        String separator = "?";
        final String text = q == null ? "" : q.trim();
        if (!text.isEmpty()) {
            link.append(separator).append("q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
            separator = "&";
        }
        if (page > 1) {
            link.append(separator).append("page=").append(page);
        }
        return link.toString();
    }

    // El returnTo que mando la pantalla, solo si de verdad es un link a esta misma pantalla (con o sin query). Viene de
    // un formulario, o sea de alguien que puede mandar lo que quiera: cualquier otra cosa (falta, otro sitio, otra
    // ruta de la app) vuelve a la lista sin busqueda. Sin llaves: Spring las toma por variables de la URL en el redirect.
    public static String safeReturnTo(final String returnTo) {
        final boolean sameScreen = SafeRedirect.isSafe(returnTo)
                && (returnTo.equals(PATH) || returnTo.startsWith(PATH + "?"))
                && returnTo.indexOf('{') < 0 && returnTo.indexOf('}') < 0;
        return sameScreen ? returnTo : PATH;
    }
}
