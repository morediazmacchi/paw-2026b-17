package ar.edu.itba.paw.webapp.util;

import java.util.LinkedHashMap;
import java.util.Map;

// Lo que el paginador (pager.tag, extraParams) repite en los links de cada pagina para que cambiar de pagina conserve
// la busqueda y, si la pantalla tiene, la pestaña o el orden elegidos. El texto vacio no se escribe.
public final class PagerParams {

    private PagerParams() {
    }

    public static Map<String, String> of(final String q) {
        final Map<String, String> params = new LinkedHashMap<>();
        if (q != null && !q.trim().isEmpty()) {
            params.put("q", q.trim());
        }
        return params;
    }

    // Ademas de la busqueda, un parametro mas (por ejemplo filtro=NEW o sort=TITLE_ASC).
    public static Map<String, String> of(final String q, final String name, final String value) {
        final Map<String, String> params = of(q);
        params.put(name, value);
        return params;
    }
}
