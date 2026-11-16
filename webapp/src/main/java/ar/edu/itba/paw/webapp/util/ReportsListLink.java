package ar.edu.itba.paw.webapp.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// A donde vuelven las acciones de "Reportes y seguimiento" (descartar, pedir cambios, dar de baja, ver
// seguimiento): mismo patron que UserListLink, ver su comentario. Sin esto, resolver un reporte desde la
// pestaña "Modificacion solicitada" con una busqueda activa volveria siempre a "Todos" sin busqueda.
public final class ReportsListLink {

    private static final String PATH = "/admin/reportes";

    private ReportsListLink() {
    }

    // La pantalla de reportes con la busqueda, la pestaña y la pagina que se estan viendo. El texto vacio, la
    // pestaña "ALL" y la primera pagina no se escriben (queda la URL limpia).
    public static String of(final String q, final String filtro, final int page) {
        final StringBuilder link = new StringBuilder(PATH);
        String separator = "?";
        final String text = q == null ? "" : q.trim();
        if (!text.isEmpty()) {
            link.append(separator).append("q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
            separator = "&";
        }
        if (filtro != null && !filtro.isEmpty() && !"ALL".equals(filtro)) {
            link.append(separator).append("filtro=").append(filtro);
            separator = "&";
        }
        if (page > 1) {
            link.append(separator).append("page=").append(page);
        }
        return link.toString();
    }

    // El returnTo que mando la pantalla, solo si de verdad es un link a esta misma pantalla (con o sin
    // query). Viene de un formulario, o sea de alguien que puede mandar lo que quiera.
    public static String safeReturnTo(final String returnTo) {
        final boolean sameScreen = SafeRedirect.isSafe(returnTo)
                && (returnTo.equals(PATH) || returnTo.startsWith(PATH + "?"))
                && returnTo.indexOf('{') < 0 && returnTo.indexOf('}') < 0;
        return sameScreen ? returnTo : PATH;
    }

    // Agrega "abierto=id" al returnTo ya validado, para que la pantalla auto-abra el seguimiento de ese
    // reporte al volver (ver reportes.jsp). Separador segun si returnTo ya trae query o no.
    public static String withOpened(final String safeReturnTo, final long reportId) {
        return safeReturnTo + (safeReturnTo.indexOf('?') < 0 ? "?" : "&") + "abierto=" + reportId;
    }
}
