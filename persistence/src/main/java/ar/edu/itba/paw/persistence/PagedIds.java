package ar.edu.itba.paw.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Paso 2 del modelo 1+1: el IN no garantiza ningun orden, asi que las entidades cargadas se
// reordenan segun el orden de los ids que decidio la primera consulta (la que pagina).
final class PagedIds {

    private PagedIds() {
    }

    static <T> List<T> inOrder(final List<Long> ids, final Collection<T> loaded, final Function<T, Long> idOf) {
        final Map<Long, T> byId = new HashMap<>();
        for (final T item : loaded) {
            byId.put(idOf.apply(item), item);
        }
        final List<T> ordered = new ArrayList<>(ids.size());
        for (final Long id : ids) {
            final T item = byId.get(id);
            if (item != null) {
                ordered.add(item);
            }
        }
        return ordered;
    }
}
