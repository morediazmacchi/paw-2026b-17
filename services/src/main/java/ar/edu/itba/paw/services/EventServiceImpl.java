// Implementa el contrato EventService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
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
    private final RegistrationDao registrationDao;

    @Autowired
    public EventServiceImpl(final EventDao eventDao, final UserService userService,
                            final NotificationService notificationService, final RegistrationDao registrationDao) {
        this.eventDao = eventDao;
        this.userService = userService;
        this.notificationService = notificationService;
        this.registrationDao = registrationDao;
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

    @Override
    @Transactional
    public EventUpdateResult update(final long eventId, final Event changes, final String callerEmail) {
        final Optional<Event> existing = eventDao.findById(eventId);
        if (!existing.isPresent() || !existing.get().getCreatorEmail().equalsIgnoreCase(callerEmail)) {
            return EventUpdateResult.NOT_FOUND;
        }
        if (changes.getMaxCapacity() != null && changes.getMaxCapacity() < registrationDao.countConfirmed(eventId)) {
            return EventUpdateResult.CAPACITY_TOO_LOW;
        }
        changes.setId(eventId);
        eventDao.update(changes);
        return EventUpdateResult.UPDATED;
    }

    @Override
    @Transactional
    public EventCancelResult cancel(final long eventId, final String callerEmail) {
        final Optional<Event> existing = eventDao.findById(eventId);
        if (!existing.isPresent() || !existing.get().getCreatorEmail().equalsIgnoreCase(callerEmail)) {
            return EventCancelResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (event.isCancelled()) {
            return EventCancelResult.ALREADY_CANCELLED;
        }
        eventDao.cancel(eventId);
        // Solo a los confirmados: a alguien pendiente/rechazado/en lista de
        // espera nunca le llego a tener un lugar, no hay nada que "perder".
        for (final Registration registration : registrationDao.findByEventId(eventId)) {
            if (Registration.STATUS_CONFIRMED.equals(registration.getStatus())) {
                notificationService.notifyEventCancelled(event, registration);
            }
        }
        return EventCancelResult.CANCELLED;
    }
}
