// Contrato de servicio para favoritos. Implementacion: FavoriteServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;

import java.util.List;

public interface FavoriteService {

    /**
     * Guarda el evento como favorito de ese usuario. Idempotente (ver {@link #remove}):
     * si ya era favorito, no hace nada y devuelve false.
     *
     * @throws EventNotFoundException si el evento no existe o {@code user} no puede
     *                                verlo (mismo chequeo que EventAccessService#getViewableEvent).
     */
    boolean add(User user, long eventId);

    boolean remove(long userId, long eventId);
    boolean isFavorite(long userId, long eventId);

    // "Mis favoritos": mas recientes primero.
    List<Long> findEventIdsByUser(long userId);
}
