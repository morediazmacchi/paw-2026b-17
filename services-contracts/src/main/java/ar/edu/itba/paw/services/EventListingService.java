// Arma la pantalla /eventos. Implementacion: EventListingServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventSearchCriteria;
import org.springframework.transaction.annotation.Transactional;

public interface EventListingService {

    /**
     * Todo lo que muestra /eventos en una sola lectura: la pagina de resultados de {@link EventService#search}
     * (quien mira sale de {@code criteria.getViewerId()}, null = sin sesion), el banner con los
     * {@code featuredCount} eventos mas inscriptos, las categorias para los filtros y, solo con sesion, cuales de
     * los eventos de la pagina son favoritos y el estado de la inscripcion propia en cada uno.
     * {@code checkSavedSearch} pide ademas saber si estos filtros ya estan guardados (la pantalla solo lo muestra
     * cuando hay filtros puestos: sin pedirlo no se hace la consulta).
     */
    @Transactional(readOnly = true)
    EventListing getListing(EventSearchCriteria criteria, int featuredCount, boolean checkSavedSearch);
}
