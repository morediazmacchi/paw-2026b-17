// Implementa el contrato EventListingService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Aparte de EventServiceImpl por la misma razon que EventDetailServiceImpl: junta varios services (favoritos,
// inscripciones, busquedas guardadas) y esos dependen de EventService; un service que nadie inyecta rompe el ciclo
// sin @Lazy.
@Service
public class EventListingServiceImpl implements EventListingService {

    private final EventService eventService;
    private final CategoryService categoryService;
    private final FavoriteService favoriteService;
    private final RegistrationService registrationService;
    private final SavedSearchService savedSearchService;

    @Autowired
    public EventListingServiceImpl(final EventService eventService, final CategoryService categoryService,
                                   final FavoriteService favoriteService,
                                   final RegistrationService registrationService,
                                   final SavedSearchService savedSearchService) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.favoriteService = favoriteService;
        this.registrationService = registrationService;
        this.savedSearchService = savedSearchService;
    }

    @Override
    @Transactional(readOnly = true)
    public EventListing getListing(final EventSearchCriteria criteria, final int featuredCount,
                                   final boolean checkSavedSearch) {
        final EventSearchResult search = eventService.search(criteria);
        final List<Event> featured = eventService.findMostRegistered(featuredCount);

        final Long viewerId = criteria.getViewerId();
        if (viewerId == null) {
            return new EventListing(search, featured, categoryService.findAll(), Collections.emptySet(),
                    Collections.emptyMap(), false);
        }
        // Solo de los eventos de ESTA pagina: no la lista completa de favoritos ni de inscripciones.
        final List<Long> pageEventIds = search.getPage().getContent().stream()
                .map(Event::getId).collect(Collectors.toList());
        final Set<Long> favoriteEventIds = favoriteService.findFavoriteEventIds(viewerId, pageEventIds);
        final Map<Long, RegistrationStatus> statuses =
                registrationService.findStatusesByEventIds(viewerId, pageEventIds);
        // Una busqueda guardada son los filtros, no el texto de la barra: isSaved() ignora el texto.
        final boolean searchSaved = checkSavedSearch && savedSearchService.isSaved(viewerId, criteria);
        return new EventListing(search, featured, categoryService.findAll(), favoriteEventIds, statuses, searchSaved);
    }
}
