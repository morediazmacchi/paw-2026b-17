// Contrato de servicio para favoritos. Implementacion: FavoriteServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;

import java.util.List;

public interface FavoriteService {

    /**
     * Guarda el evento como favorito de ese usuario. Idempotente (ver {@link #remove}):
     * si ya era favorito, no hace nada y devuelve false.
     * Que {@code user} pueda ver el evento NO se valida aca: lo decide @PreAuthorize en el
     * controller (ver EventSecurity.canView).
     */
    boolean add(User user, long eventId);

    boolean remove(long userId, long eventId);
    boolean isFavorite(long userId, long eventId);

    // "Mis favoritos": mas recientes primero.
    List<Long> findEventIdsByUser(long userId);

    /**
     * "Mis favoritos" de a una pagina, mas recientes primero, SIN los de un
     * evento privado al que el usuario ya no tiene acceso (ej. le revocaron la
     * invitacion). El filtro se aplica ANTES de cortar la pagina: asi el total,
     * el numero de paginas y el contenido de cada pagina coinciden. El favorito
     * no se borra, solo deja de mostrarse; si recupera el acceso, vuelve a
     * aparecer. Una pagina mayor a la ultima devuelve la ultima, una menor a 1
     * la primera; sin favoritos visibles, la pagina 1 vacia.
     */
    PageResult<Event> findViewablePage(User user, int page);

    /**
     * Cuantos favoritos puede ver hoy el usuario (el badge de "Favoritos"): un COUNT en la base, con la misma
     * regla de visibilidad que findViewablePage(). Un admin ve todos los eventos, por eso hace falta saber si lo es.
     */
    long countViewable(long userId, boolean isAdmin);
}
