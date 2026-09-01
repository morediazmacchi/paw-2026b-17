package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.persistence.EventDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    private final EventDao eventDao;

    @Autowired
    public EventServiceImpl(final EventDao eventDao) {
        this.eventDao = eventDao;
    }

    @Override
    public Event create(final Event event) { return eventDao.create(event); }
    @Override
    public Optional<Event> findById(final long id) { return eventDao.findById(id); }
    @Override
    public List<Event> findAll() { return eventDao.findAll(); }
    @Override
    public List<Event> findByCategoria(final long categoriaId) { return eventDao.findByCategoria(categoriaId); }
}
