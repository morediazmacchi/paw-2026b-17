// Implementa el contrato EventService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.User;
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
    private final NotificationService notificationService;

    @Autowired
    public EventServiceImpl(final EventDao eventDao, final UserService userService,
                            final NotificationService notificationService) {
        this.eventDao = eventDao;
        this.userService = userService;
        this.notificationService = notificationService;
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
        User creator = null;
        try {
            creator = userService.findOrCreate(created.getCreatorEmail(), null);
        } catch (final RuntimeException e) {
            LOGGER.warn("No se pudo crear/actualizar el user para {}", created.getCreatorEmail(), e);
        }
        // Devuelve enseguida: el mail sale en otro hilo (@Async).
        notificationService.notifyEventCreated(created, creator == null ? null : creator.getName());
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
    public List<Event> findByCreatorEmail(final String email) {
        return eventDao.findByCreatorEmail(email.trim().toLowerCase());
    }
    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> search(final EventSearchCriteria criteria) {
        final long totalElements = eventDao.countSearch(criteria);
        final int totalPages = criteria.getPageSize() <= 0 ? 0
                : (int) Math.ceil((double) totalElements / criteria.getPageSize());
        // Si pidieron una pagina que no existe, cae a la ultima con resultados (nunca vacia por eso).
        final int effectivePage = totalPages == 0 ? 1 : Math.min(Math.max(criteria.getPage(), 1), totalPages);
        criteria.setPage(effectivePage);

        final List<Event> content = eventDao.search(criteria);
        return new PageResult<>(content, totalElements, effectivePage, criteria.getPageSize());
    }
}
