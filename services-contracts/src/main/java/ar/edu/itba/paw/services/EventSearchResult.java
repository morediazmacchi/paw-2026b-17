// El resultado de buscar en /eventos: la pagina de eventos, el orden que de verdad se aplico y los
// ordenes que la pantalla puede ofrecer. Lo devuelve EventService.search().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSort;

import java.util.List;

public class EventSearchResult {

    private final PageResult<Event> page;
    private final EventSort effectiveSort;
    private final List<EventSort> availableSorts;

    public EventSearchResult(final PageResult<Event> page, final EventSort effectiveSort,
                             final List<EventSort> availableSorts) {
        this.page = page;
        this.effectiveSort = effectiveSort;
        this.availableSorts = availableSorts;
    }

    public PageResult<Event> getPage() { return page; }

    // El orden que se uso: puede no ser el pedido (ver EventService.search()).
    public EventSort getEffectiveSort() { return effectiveSort; }

    // Los ordenes que se le ofrecen a quien navega.
    public List<EventSort> getAvailableSorts() { return availableSorts; }
}
