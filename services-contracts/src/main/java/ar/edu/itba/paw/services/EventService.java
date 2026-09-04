// Contrato de servicio para Event. Implementacion: EventServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

import java.util.List;
import java.util.Optional;

public interface EventService {
    Event create(Event event);
    Optional<Event> findById(long id);
    List<Event> findAll();
    List<Event> findByCategory(long categoryId);
}
