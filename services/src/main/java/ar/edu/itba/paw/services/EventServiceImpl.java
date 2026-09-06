// Implementa el contrato EventService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.persistence.EventDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);

    private final EventDao eventDao;
    private final UserService userService;

    @Autowired
    public EventServiceImpl(final EventDao eventDao, final UserService userService) {
        this.eventDao = eventDao;
        this.userService = userService;
    }

    @Override
    @Transactional
    public Event create(final Event event) {
        final Event created;
        try {
            created = eventDao.create(event);
        } catch (final DataIntegrityViolationException e) {
            throw new IllegalArgumentException("La categoria elegida no existe", e);
        }
        try {
            userService.findOrCreate(created.getCreatorEmail(), null);
        } catch (final RuntimeException e) {
            LOGGER.warn("No se pudo crear/actualizar el user para {}", created.getCreatorEmail(), e);
        }
        return created;
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(final long id) { return eventDao.findById(id); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findAll() { return eventDao.findAll(); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findByCategory(final long categoryId) { return eventDao.findByCategory(categoryId); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> search(final EventSearchCriteria criteria) { return eventDao.search(criteria); }
}
