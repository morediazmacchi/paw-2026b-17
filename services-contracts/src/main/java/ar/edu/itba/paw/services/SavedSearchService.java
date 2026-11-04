// Busquedas guardadas ("watchlist"): guardar los filtros de /eventos y avisar a la bandeja y por mail cuando se
// publica un evento nuevo que los cumple. Implementacion: SavedSearchServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventSearchCriteria;

import java.util.List;

public interface SavedSearchService {

    // Cuantas busquedas puede tener guardadas una persona.
    int MAX_PER_USER = 10;

    /**
     * Guarda los filtros de la busqueda (categorias, barrios, precio y fechas; el resto de criteria se ignora). No la
     * guarda dos veces ni pasa de MAX_PER_USER: el resultado dice cual de los casos fue.
     */
    SaveSearchResult save(long userId, EventSearchCriteria criteria);

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
