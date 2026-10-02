// Contrato de servicio para favoritos. Implementacion: FavoriteServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import java.util.List;

public interface FavoriteService {
    boolean add(long userId, long eventId);
    boolean remove(long userId, long eventId);
    boolean isFavorite(long userId, long eventId);

    // "Mis favoritos": mas recientes primero.
    List<Long> findEventIdsByUser(long userId);
}
