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

    // Cuantos favoritos tiene, de cualquier evento (un COUNT, sin traer las filas). Es lo que ve un admin,
    // que puede ver todos los eventos.
    long countByUserId(long userId);

    // Cuantos de sus favoritos puede ver una persona que NO es admin (un COUNT): la misma regla de
    // EventAccessService.canView en SQL. Es el creador del evento, o el evento esta aprobado y es publico,
    // o es privado y tiene una inscripcion (del estado que sea) o una invitacion.
    long countViewableByUserId(long userId);
}
