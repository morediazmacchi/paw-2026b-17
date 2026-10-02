// Contrato de persistencia para favoritos. Implementacion: FavoriteJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import java.util.List;

public interface FavoriteDao {
    // true si se agrego; false si ya era favorito.
    boolean add(long userId, long eventId);
    // true si estaba y se saco.
    boolean remove(long userId, long eventId);
    boolean isFavorite(long userId, long eventId);

    // Mas recientes primero.
    List<Long> findEventIdsByUser(long userId);
}
