// Todo lo que necesita la pantalla /eventos: la pagina de eventos (con el orden que de verdad se aplico y los que se
// ofrecen), el banner, las categorias para los filtros y, para quien tiene sesion, sus favoritos y sus inscripciones
// entre los eventos de la pagina. Lo devuelve EventListingService.getListing().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventListing {

    private final EventSearchResult search;
    private final List<Event> featured;
    private final List<Category> categories;
    private final Set<Long> favoriteEventIds;
    private final Map<Long, RegistrationStatus> registrationStatuses;
    private final boolean searchSaved;

    public EventListing(final EventSearchResult search, final List<Event> featured, final List<Category> categories,
                        final Set<Long> favoriteEventIds, final Map<Long, RegistrationStatus> registrationStatuses,
                        final boolean searchSaved) {
        this.search = search;
        this.featured = featured;
        this.categories = categories;
        this.favoriteEventIds = favoriteEventIds;
        this.registrationStatuses = registrationStatuses;
        this.searchSaved = searchSaved;
    }

    public PageResult<Event> getPage() { return search.getPage(); }

    // El orden que se uso: puede no ser el pedido (ver EventService.search()).
    public EventSort getEffectiveSort() { return search.getEffectiveSort(); }

    // Los ordenes que se le ofrecen a quien navega.
    public List<EventSort> getAvailableSorts() { return search.getAvailableSorts(); }

    // El banner: los eventos con mas inscriptos en este momento.
    public List<Event> getFeatured() { return featured; }

    public List<Category> getCategories() { return categories; }

    // Cuales de los eventos de ESTA pagina son favoritos de quien mira. Vacio sin sesion.
    public Set<Long> getFavoriteEventIds() { return favoriteEventIds; }

    // Estado de la inscripcion propia en cada evento de la pagina; un evento sin entrada = nunca se inscribio (o se dio
    // de baja). Vacio sin sesion.
    public Map<Long, RegistrationStatus> getRegistrationStatuses() { return registrationStatuses; }

    // Estos filtros ya estan guardados como busqueda de quien mira. Siempre false sin sesion o si no se pidio mirarlo.
    public boolean isSearchSaved() { return searchSaved; }
}
