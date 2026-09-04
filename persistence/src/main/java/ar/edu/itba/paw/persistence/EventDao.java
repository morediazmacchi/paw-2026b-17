package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;

import java.util.List;
import java.util.Optional;

public interface EventDao {
    Event create(Event event);
    Optional<Event> findById(long id);
    List<Event> findAll();
    List<Event> findByCategory(long categoryId);
}
