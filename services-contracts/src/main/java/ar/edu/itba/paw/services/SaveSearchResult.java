// Resultado de guardar una busqueda (SavedSearchService.save()).
package ar.edu.itba.paw.services;

public enum SaveSearchResult {
    /** Se guardo: de ahora en mas se le avisa de los eventos nuevos que la cumplan. */
    SAVED,

    /** Ya tenia guardada una busqueda con exactamente esos filtros. */
    ALREADY_SAVED
}
