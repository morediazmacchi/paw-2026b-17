// Implementa el contrato EventService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.dto.NewImageData;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.InvalidEventException;
import ar.edu.itba.paw.persistence.EventDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    private final EventDao eventDao;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RegistrationService registrationService;
    private final InboxService inboxService;
    private final ImageService imageService;

    @Autowired
    public EventServiceImpl(final EventDao eventDao, final UserService userService,
                            final NotificationService notificationService,
                            final RegistrationService registrationService,
                            final InboxService inboxService, final ImageService imageService) {
        this.eventDao = eventDao;
        this.userService = userService;
        this.notificationService = notificationService;
        this.registrationService = registrationService;
        this.inboxService = inboxService;
        this.imageService = imageService;
    }

    // Sube cada imagen y devuelve sus ids, en el mismo orden en que se cargaron
    // (ese orden es el de la galeria, ver Event.imageIds -- la primera es la
    // portada). A proposito DENTRO de la transaccion de quien llama (create()/
    // update()): si algo despues falla (otra imagen invalida, una categoria que
    // no existe), Spring deshace esto tambien -- antes esto vivia en el
    // controller, fuera de cualquier transaccion, y una imagen ya subida con
    // exito podia quedar huerfana si el evento despues no se llegaba a crear.
    private List<Long> uploadImages(final List<NewImageData> images) {
        final List<Long> ids = new ArrayList<>();
        for (final NewImageData image : images) {
            final Image saved = imageService.create(image.getData(), image.getContentType(), image.getFileName());
            ids.add(saved.getId());
        }
        return ids;
    }

    @Override
    @Transactional
    public Event create(final EventDTO dto) {
        Objects.requireNonNull(dto, "dto");
        final List<Long> imageIds = uploadImages(dto.getNewImages());
        final Event event = dto.toEvent(dto.getCreatorEmail(), imageIds);
        event.setCreatorId(dto.getCreatorId());

        final Event created;
        try {
            created = eventDao.create(event);
        } catch (final DataIntegrityViolationException e) {
            throw new InvalidEventException(categoryConstraintViolated(e)
                    ? "event.publish.categoryNotFound" : "event.publish.invalidData", e);
        }
        final User creator = userService.findById(created.getCreatorId()).orElse(null);
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
    @Transactional(rollbackFor = Exception.class)
    public EventUpdateOutcome update(final long eventId, final EventDTO dto, final long callerId) {
        Objects.requireNonNull(dto, "dto");
        // Con lock (SELECT ... FOR UPDATE), igual que register(): mientras se
        // chequea y se cambia el cupo, nadie se puede anotar. Sin esto, alguien
        // podia confirmarse entre el chequeo y el UPDATE y quedar mas gente que cupo.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent() || !Long.valueOf(callerId).equals(existing.get().getCreatorId())) {
            throw new EventNotFoundException(eventId);
        }
        if (existing.get().isCancelled()) {
            // Se cancelo justo entre medio: quien llama ya deberia haber chequeado
            // esto antes (ver EventAccessService#getEditableEvent), esto es la
            // defensa de la carrera, no el chequeo principal.
            throw new EventCancelledException(eventId);
        }
        // No alcanza con los confirmados: una invitacion sin contestar o un
        // pedido pendiente tambien tienen su lugar guardado (ver occupiedSpots).
        if (dto.getMaxCapacity() != null && dto.getMaxCapacity() < registrationService.occupiedSpots(eventId)) {
            throw new InvalidEventException("cupo.muyBajo", "maxCapacity");
        }
        // Si no se subio ninguna foto nueva, se mantiene la galeria que el
        // evento ya tenia; si se subio al menos una, las nuevas reemplazan toda
        // la galeria vieja. Subirlas aca (no en el controller) las deja dentro
        // de esta misma transaccion: si algo de abajo fallara, se deshacen
        // tambien, no quedan huerfanas.
        final List<Long> imageIds = dto.getNewImages().isEmpty()
                ? existing.get().getImageIds()
                : uploadImages(dto.getNewImages());
        final Event changes = dto.toEvent(existing.get().getCreatorEmail(), imageIds);
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
        final int promoted = registrationService.promoteFromWaitlist(eventId);
        return new EventUpdateOutcome(changes, promoted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventCancelResult cancel(final long eventId, final long callerId) {
        // Con for update.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent() || !Long.valueOf(callerId).equals(existing.get().getCreatorId())) {
            throw new EventNotFoundException(eventId);
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
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
    public boolean setFeatured(final long eventId, final boolean featured) {
        return eventDao.setFeatured(eventId, featured);
    }

    // insertCategories()/insertImages() (ver EventJdbcDao.create()) son las dos
    // unicas FK que puede violar este insert: category_id contra una categoria
    // que no existe, o image_id contra una imagen que ya no esta (subida y
    // despues borrada entre medio, caso rarisimo). Antes se asumia que
    // CUALQUIER violacion era la categoria; esto distingue mirando el nombre
    // de la constraint en el mensaje de la excepcion (event_category_...),
    // que Postgres arma solo con esa convencion a partir del nombre de tabla
    // y columna.
    private static boolean categoryConstraintViolated(final DataIntegrityViolationException e) {
        final Throwable cause = e.getMostSpecificCause();
        final String message = cause == null ? null : cause.getMessage();
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("event_category");
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
