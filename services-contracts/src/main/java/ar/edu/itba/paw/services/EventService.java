// Contrato de servicio para Event. Implementacion: EventServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;

import java.util.List;
import java.util.Optional;

public interface EventService {
    Event create(Event event);
    Optional<Event> findById(long id);
    List<Event> findAll();
    List<Event> findByCategory(long categoryId);
    // Pagina la respuesta: criteria.getPage() puede caer a la ultima pagina con
    // resultados si pedis una que no existe.
    PageResult<Event> search(EventSearchCriteria criteria);

    // "Mis eventos": todos los creados por este email, mas recientes primero.
    List<Event> findByCreatorEmail(String email);

    /**
     * Edita un evento existente. No cambia quien lo creo ni cuando se creo
     * (esos campos de "changes" se ignoran).
     *
     * @param changes los valores nuevos; su propio id se ignora, se usa el
     *                eventId del primer parametro
     */
    EventUpdateResult update(long eventId, Event changes, String callerEmail);
}
