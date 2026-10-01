// Implementa el contrato EventService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    private final EventDao eventDao;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RegistrationService registrationService;
    private final InboxService inboxService;

    @Autowired
    public EventServiceImpl(final EventDao eventDao, final UserService userService,
                            final NotificationService notificationService,
                            final RegistrationService registrationService,
                            final InboxService inboxService) {
        this.eventDao = eventDao;
        this.userService = userService;
        this.notificationService = notificationService;
        this.registrationService = registrationService;
        this.inboxService = inboxService;
    }

    @Override
    @Transactional
    public Event create(final EventDTO dto) {
        final Event event = dto.toEvent(dto.getCreatorEmail());
        event.setCreatorId(dto.getCreatorId());

        final Event created;
        try {
            created = eventDao.create(event);
        } catch (final DataIntegrityViolationException e) {
            throw new IllegalArgumentException("La categoria elegida no existe", e);
        }
        // El creador ya viene resuelto (creatorId, ver EventController.publish()):
        // a diferencia de antes, no hace falta userService.findOrCreate() para
        // garantizar una fila en users, el Principal autenticado ya la implica.
        // Solo se busca para el nombre que va en el mail de bienvenida.
        final User creator = userService.findById(created.getCreatorId()).orElse(null);
        // Devuelve enseguida: el mail sale en otro hilo (@Async).
        notificationService.notifyEventCreated(created, creator == null ? null : creator.getName());
        return created;
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(final long id) { return eventDao.findById(id); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findByIds(final List<Long> ids) { return eventDao.findByIds(ids); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findAll() { return eventDao.findAll(); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findByCategory(final long categoryId) { return eventDao.findByCategory(categoryId); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findByCreatorId(final long creatorId) {
        return eventDao.findByCreatorId(creatorId);
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
    public EventUpdateResult update(final long eventId, final Event changes, final long callerId) {
        // Con lock (SELECT ... FOR UPDATE), igual que register(): mientras se
        // chequea y se cambia el cupo, nadie se puede anotar. Sin esto, alguien
        // podia confirmarse entre el chequeo y el UPDATE y quedar mas gente que cupo.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent() || !Long.valueOf(callerId).equals(existing.get().getCreatorId())) {
            return EventUpdateResult.NOT_FOUND;
        }
        if (existing.get().isCancelled()) {
            return EventUpdateResult.CANCELLED;
        }
        // No alcanza con los confirmados: una invitacion sin contestar o un
        // pedido pendiente tambien tienen su lugar guardado (ver occupiedSpots).
        if (changes.getMaxCapacity() != null && changes.getMaxCapacity() < registrationService.occupiedSpots(eventId)) {
            return EventUpdateResult.CAPACITY_TOO_LOW;
        }
        changes.setId(eventId);
        eventDao.update(changes);
        // Solo a los confirmados, mismo criterio que cancel(): a alguien
        // pendiente/rechazado/en lista de espera todavia no tiene un lugar
        // asegurado en base al cual le importe que cambio el evento.
        for (final Registration registration : registrationService.findByEventId(eventId)) {
            if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
                notificationService.notifyEventUpdated(changes, registration);
                inboxService.add(registration.getEmail(), InboxMessageType.EVENT_UPDATED, eventId, null, null);
            }
        }
        // Si subio el cupo (o se lo saco), los primeros de la lista de espera entran.
        // Despues del aviso de cambios: a los que entran ahora les llega ya el mail
        // de que estan confirmados, no hace falta ademas contarles que el evento cambio.
        registrationService.promoteFromWaitlist(eventId);
        return EventUpdateResult.UPDATED;
    }

    @Override
    @Transactional
    public EventCancelResult cancel(final long eventId, final long callerId) {
        final Optional<Event> existing = eventDao.findById(eventId);
        if (!existing.isPresent() || !Long.valueOf(callerId).equals(existing.get().getCreatorId())) {
            return EventCancelResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (event.isCancelled()) {
            return EventCancelResult.ALREADY_CANCELLED;
        }
        eventDao.cancel(eventId);
        notifyRegistrantsOfCancellation(event);
        return EventCancelResult.CANCELLED;
    }

    @Override
    @Transactional
    public EventCancelResult removeByAdmin(final long eventId, final String reason) {
        final Optional<Event> existing = eventDao.findById(eventId);
        if (!existing.isPresent()) {
            return EventCancelResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (event.isCancelled()) {
            return EventCancelResult.ALREADY_CANCELLED;
        }
        eventDao.removeByAdmin(eventId, reason);
        // Mismo aviso a quien estaba anotado que una cancelacion comun (para
        // ellos el efecto es el mismo: el evento ya no va). El creador, en
        // cambio, recibe un aviso aparte y distinto: a el le interesa el motivo.
        notifyRegistrantsOfCancellation(event);
        notificationService.notifyEventRemovedByAdmin(event, reason);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_REMOVED_BY_ADMIN, eventId, null, reason);
        return EventCancelResult.CANCELLED;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findFeatured(final int limit) {
        return eventDao.findFeatured(limit);
    }

    @Override
    @Transactional
    public void setFeatured(final long eventId, final boolean featured) {
        eventDao.setFeatured(eventId, featured);
    }

    // Mail: solo a los confirmados (son los que tenian un lugar que pierden).
    // Bandeja: tambien a quien estaba en lista de espera o esperando que lo
    // acepten, que si no se enteraria recien al ver el evento. Al rechazado no:
    // ese ya no esperaba nada de este evento. Comun a cancel() y removeByAdmin():
    // para quien esta anotado el motivo (el organizador o un admin) no cambia
    // el aviso que le corresponde.
    private void notifyRegistrantsOfCancellation(final Event event) {
        for (final Registration registration : registrationService.findByEventId(event.getId())) {
            if (registration.getStatus() == RegistrationStatus.CONFIRMED) {
                notificationService.notifyEventCancelled(event, registration);
            }
            if (registration.getStatus() != RegistrationStatus.REJECTED) {
                inboxService.add(registration.getEmail(), InboxMessageType.EVENT_CANCELLED, event.getId(), null, null);
            }
        }
    }
}
