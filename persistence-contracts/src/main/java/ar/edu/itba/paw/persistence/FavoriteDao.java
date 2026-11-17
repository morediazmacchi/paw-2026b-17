// Contrato de persistencia para favoritos. Implementacion: FavoriteJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface FavoriteDao {
    // true si se agrego; false si ya era favorito.
    boolean add(long userId, long eventId);
    // true si estaba y se saco.
    boolean remove(long userId, long eventId);
    boolean isFavorite(long userId, long eventId);

    // De esos eventos, cuales son favoritos de esa persona (una consulta con IN, sin traer los demas
    // favoritos). Es lo que hace falta para marcar el corazon de los eventos de UNA pagina del listado.
    Set<Long> findFavoriteEventIds(long userId, Collection<Long> eventIds);

    // Una pagina (page desde 1) de sus favoritos, de cualquier evento (con sus categorias), mas recientes
    // primero (LIMIT/OFFSET en la base). Es lo que ve un admin, que puede ver todos los eventos. Mismo orden que
    // findViewableEventsByUser().
    List<Event> findEventsByUser(long userId, int page, int pageSize);

    // Una pagina (page desde 1) de los favoritos que puede ver una persona que NO es admin (con sus categorias),
    // mas recientes primero. El filtro de visibilidad es el WHERE de la misma consulta que cuenta
    // countViewableByUserId(): total y paginas coinciden siempre.
    List<Event> findViewableEventsByUser(long userId, int page, int pageSize);

    // Cuantos favoritos tiene, de cualquier evento (un COUNT, sin traer las filas). Es lo que ve un admin,
    // que puede ver todos los eventos.
    long countByUserId(long userId);

    // Cuantos de sus favoritos puede ver una persona que NO es admin (un COUNT): la misma regla de
    // EventAccessService.canView en SQL. Es el creador del evento, o el evento esta aprobado y es publico,
    // o es privado y tiene una inscripcion (del estado que sea) o una invitacion.
    long countViewableByUserId(long userId);
}
