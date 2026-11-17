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
     * La busqueda guardada con sus filtros (para la pantalla de confirmar la baja que llega desde el mail), o vacio
     * si no existe. De quien es NO se valida aca: lo decide @PreAuthorize en el controller (ver SavedSearchSecurity).
     */
    Optional<SavedSearch> findById(long savedSearchId);

    /**
     * El id de la persona duenia de la busqueda, o vacio si no existe: es lo unico que necesita la autorizacion
     * (ver SavedSearchSecurity), sin cargar la busqueda.
     */
    Optional<Long> findOwnerId(long savedSearchId);

    /**
     * Borra la busqueda guardada (ya no se le avisa a su duenia). false si ya no existia (por ejemplo, otra pestana
     * la borro justo antes). Que la busqueda sea de quien la pide lo decide @PreAuthorize en el controller.
     */
    boolean delete(long savedSearchId);

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
