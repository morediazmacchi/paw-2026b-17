// Contrato de persistencia para Event. Implementacion: EventJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;

import java.util.List;
import java.util.Optional;

public interface EventDao {
    Event create(Event event);
    Optional<Event> findById(long id);

    // SELECT ... FOR UPDATE: usar solo dentro de un @Transactional que chequee cupo.
    Optional<Event> findByIdForUpdate(long id);

    List<Event> findAll();
    List<Event> findByCategory(long categoryId);

    // Combina todos los filtros con AND; un campo en null en criteria significa "sin filtrar por esto".
    List<Event> search(EventSearchCriteria criteria);
}
