// Busquedas guardadas ("watchlist"): guardar los filtros de /eventos y avisar a la bandeja y por mail cuando se
// publica un evento nuevo que los cumple. Implementacion: SavedSearchServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.SavedSearch;

import java.util.List;
import java.util.Optional;

public interface SavedSearchService {

    /**
     * Guarda los filtros de la busqueda (categorias, barrios, precio y fechas; el resto de criteria se ignora). No la
     * guarda dos veces; no hay maximo de busquedas por persona. El resultado dice cual de los casos fue.
     */
    SaveSearchResult save(long userId, EventSearchCriteria criteria);

    /**
     * Una pagina de las busquedas guardadas de la persona ("Mis listas"), las mas nuevas primero y con sus filtros ya
     * cargados. La pagina pedida se ajusta a la ultima que tenga busquedas.
     */
    PageResult<SavedSearch> findPageByUser(long userId, int page);

    /**
     * La busqueda guardada con sus filtros, solo si es de esa persona: vacio si no existe o es de otra (para la
     * pantalla de confirmar la baja que llega desde el mail).
     */
    Optional<SavedSearch> findByIdForUser(long userId, long savedSearchId);

    /** Borra la busqueda guardada si es de esa persona (ya no se le avisa). false si no existe o es de otra. */
    boolean delete(long userId, long savedSearchId);

    /** Si la persona ya tiene guardada una busqueda con esos mismos filtros (para el boton de /eventos). */
    boolean isSaved(long userId, EventSearchCriteria criteria);

    /** Para recorrer todas las busquedas guardadas de a tandas (ver SavedSearchScheduler). */
    List<Long> findIdsAfter(long afterId, int limit);

    /**
     * Si desde el ultimo aviso se aprobaron eventos que cumplen la busqueda, le avisa a su duenio (bandeja + mail
     * despues del commit) y corre la marca de "ultimo aviso". Una transaccion por busqueda. true si hubo aviso.
     */
    boolean notifyNewEvents(long savedSearchId);
}
