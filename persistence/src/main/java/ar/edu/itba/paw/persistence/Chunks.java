package ar.edu.itba.paw.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parte una lista de ids en tandas para usarlas en un "IN (...)": un IN con una lista sin tope no anda (el driver de
 * Postgres admite como mucho 32.767 parametros por sentencia). Las sentencias masivas (por ejemplo pasar de golpe a
 * toda la lista de espera de un evento) repiten la misma sentencia una vez por tanda.
 */
final class Chunks {

    // Cuantos ids entran en cada IN.
    static final int IN_LIST_SIZE = 1000;

    private Chunks() {
    }

    static <T> List<List<T>> of(final Collection<T> items) {
        final List<T> all = new ArrayList<>(items);
        final List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < all.size(); from += IN_LIST_SIZE) {
            chunks.add(all.subList(from, Math.min(from + IN_LIST_SIZE, all.size())));
        }
        return chunks;
    }
}
