// Contrato de persistencia para favoritos. Implementacion: FavoriteJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import java.util.List;

public interface FavoriteDao {
    void add(long userId, long eventId);
    void remove(long userId, long eventId);
    boolean isFavorite(long userId, long eventId);

    // Mas recientes primero.
    List<Long> findEventIdsByUser(long userId);
}
