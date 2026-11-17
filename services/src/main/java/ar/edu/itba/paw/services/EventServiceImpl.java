// Implementa el contrato EventService (modulo services-contracts).
package ar.edu.itba.paw.services;

import java.time.Clock;
import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.dto.NewImageData;
import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.SimilarityParameters;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.EventRejectedException;
import ar.edu.itba.paw.model.exception.InvalidEventException;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import ar.edu.itba.paw.persistence.EventDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    static final int PAGE_SIZE = 12;

    private final EventDao eventDao;
    private final UserService userService;
    private final NotificationService notificationService;
    private final InboxService inboxService;
    private final ImageService imageService;
    // Los coeficientes de S(a, b) (ver findSimilar() y search() con RECOMENDADOS): los pasa tal cual al dao.
    private final SimilarityParameters similarityParameters;
    private final Clock clock;
    // Para anunciar que un evento cambio, se dio de baja o se aprobo (ver EventUpdatedEvent, EventCancelledEvent y
    // EventApprovedEvent): reaccionan RegistrantsEventListener (dentro de esta transaccion: bandeja y lista de
    // espera) y MassNotifier (los mails masivos, en segundo plano y despues del commit). Asi este service no depende
    // de RegistrationService ni de quien busca a los destinatarios.
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public EventServiceImpl(final EventDao eventDao, final UserService userService,
                            final NotificationService notificationService,
                            final InboxService inboxService, final ImageService imageService,
                            final SimilarityParameters similarityParameters,
                            final ApplicationEventPublisher eventPublisher) {
        this(eventDao, userService, notificationService, inboxService, imageService, similarityParameters,
                eventPublisher, Clock.systemUTC());
    }

    EventServiceImpl(final EventDao eventDao, final UserService userService,
                     final NotificationService notificationService,
                     final InboxService inboxService, final ImageService imageService,
                     final SimilarityParameters similarityParameters,
                     final ApplicationEventPublisher eventPublisher, final Clock clock) {
        this.eventDao = eventDao;
        this.userService = userService;
        this.notificationService = notificationService;
        this.inboxService = inboxService;
        this.imageService = imageService;
        this.similarityParameters = similarityParameters;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // Sube cada imagen y devuelve sus ids, en el mismo orden en que se cargaron
    // (ese orden es el de la galeria, ver Event.imageIds -- la primera es la
    // portada). A proposito DENTRO de la transaccion de quien llama (create()/
    // update()): si algo despues falla (otra imagen invalida, una categoria que
    // no existe), Spring deshace esto tambien y no queda ninguna imagen huerfana.
    private List<Long> uploadImages(final List<NewImageData> images) {
        // El form ya lo valida para dar feedback; esto es la defensa del service. Antes de subir nada.
        if (images.size() > Event.MAX_IMAGES) {
            throw new InvalidImageException(InvalidImageException.Reason.TOO_MANY);
        }
        final List<Long> ids = new ArrayList<>();
        for (final NewImageData image : images) {
            final Image saved = imageService.create(image.getData(), image.getContentType(), image.getFileName());
            ids.add(saved.getId());
        }
        return ids;
    }

    @Override
    @Transactional
    public EventCreation create(final long creatorId, final EventDTO dto) {
        Objects.requireNonNull(dto, "dto");
        // Se lee la cuenta para el aviso a quien lo crea (su nombre). Dentro de la transaccion findById la trae una
        // vez y el DAO apunta a esa misma instancia.
        final User creator = userService.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(creatorId));
        // Se validan las reglas del evento ANTES de escribir (subir imagenes incluido).
        validateEventDate(dto);
        validateHasCategories(dto);
        final List<Long> imageIds = uploadImages(dto.getNewImages());

        final Event created;
        try {
            created = eventDao.create(creatorId, dto, imageIds);
        } catch (final DataIntegrityViolationException e) {
            throw new InvalidEventException(categoryConstraintViolated(e)
                    ? InvalidEventException.Reason.CATEGORY_NOT_FOUND : InvalidEventException.Reason.INVALID_DATA, e);
        }
        notificationService.notifyEventPendingApproval(created, creator.getName());
        return new EventCreation(created, created.isPrivate());
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(final long id) { return eventDao.findById(id); }
    @Override
    @Transactional(readOnly = true)
    public Optional<EventAccessSummary> findAccessById(final long id) { return eventDao.findAccessById(id); }
    @Override
    @Transactional(readOnly = true)
    public List<Event> findByIds(final List<Long> ids) { return eventDao.findByIds(ids); }
    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findPageByCreatorId(final long creatorId, final int page) {
        final long total = eventDao.countByCreatorId(creatorId);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(eventDao.findByCreatorId(creatorId, effectivePage, PAGE_SIZE),
                total, effectivePage, PAGE_SIZE);
    }
    @Override
    @Transactional(readOnly = true)
    public long countByCreatorId(final long creatorId) {
        return eventDao.countByCreatorId(creatorId);
    }
    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findAllEventsPage(final String titleQuery, final AdminEventSort sort, final int page) {
        // Un texto en blanco es "sin filtro": el DAO solo distingue null.
        final String trimmed = titleQuery == null ? "" : titleQuery.trim();
        final String filter = trimmed.isEmpty() ? null : trimmed;
        final long total = eventDao.countAll(filter);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(eventDao.findAll(filter, sort, effectivePage, PAGE_SIZE), total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public EventSearchResult search(final EventSearchCriteria criteria) {
        final boolean hasHistory = criteria.getViewerId() != null
                && eventDao.hasAttendanceHistory(criteria.getViewerId(), Date.from(clock.instant()));
        final boolean hasText = criteria.hasSearchTerms();
        // RECOMENDADOS solo se ofrece a quien tiene historial: sin el, el orden seria identico a "Mas
        // proximos" y la opcion no diria nada. RELEVANCIA solo si se busco texto: sin texto no hay con que comparar.
        final List<EventSort> availableSorts = new ArrayList<>();
        for (final EventSort option : EventSort.values()) {
            if ((hasHistory || option != EventSort.RECOMENDADOS) && (hasText || option != EventSort.RELEVANCIA)) {
                availableSorts.add(option);
            }
        }
        final EventSort sort = effectiveSort(criteria.getSort(), hasHistory, hasText);
        criteria.setSort(sort);
        return new EventSearchResult(searchPage(criteria), sort, availableSorts);
    }

    // El orden que pidio la persona si se puede aplicar. Si no pidio ninguno (null) o pidio uno que no tiene sentido
    // (RECOMENDADOS sin historial, RELEVANCIA sin texto buscado), se usa el de por defecto: RELEVANCIA si busco texto
    // (lo que buscaba pesa mas que lo que le recomendamos), si no RECOMENDADOS para quien ya asistio a algo y
    // FECHA_ASC para el resto.
    private static EventSort effectiveSort(final EventSort requested, final boolean hasHistory, final boolean hasText) {
        final boolean applicable = requested != null
                && !(requested == EventSort.RECOMENDADOS && !hasHistory)
                && !(requested == EventSort.RELEVANCIA && !hasText);
        if (applicable) {
            return requested;
        }
        if (hasText) {
            return EventSort.RELEVANCIA;
        }
        return hasHistory ? EventSort.RECOMENDADOS : EventSort.FECHA_ASC;
    }

    private PageResult<Event> searchPage(final EventSearchCriteria criteria) {
        criteria.setPageSize(PAGE_SIZE);
        final long totalElements = eventDao.countSearch(criteria);
        // Si pidieron una pagina que no existe, cae a la ultima con resultados (nunca vacia por eso).
        final int effectivePage = PageResult.clampPage(criteria.getPage(), totalElements, criteria.getPageSize());
        criteria.setPage(effectivePage);

        // RECOMENDADOS no es una columna: su puntaje lo calcula la base, que devuelve la pagina ya ordenada (solo se
        // llega con historial, y por lo tanto con viewerId). El total es el del listado: mismo WHERE.
        final List<Event> content = criteria.getSort() == EventSort.RECOMENDADOS
                ? eventDao.findRecommendedPage(criteria, similarityParameters)
                : eventDao.search(criteria);
        return new PageResult<>(content, totalElements, effectivePage, criteria.getPageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findSimilar(final long eventId, final Long viewerId) {
        return eventDao.findSimilarTo(eventId, viewerId, similarityParameters);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventUpdateOutcome update(final long eventId, final EventDTO dto) {
        Objects.requireNonNull(dto, "dto");
        // Con lock (SELECT ... FOR UPDATE), igual que register(): mientras se
        // chequea y se cambia el cupo, nadie se puede anotar. Sin esto, alguien
        // podia confirmarse entre el chequeo y el UPDATE y quedar mas gente que cupo.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            throw new EventNotFoundException(eventId);
        }
        if (existing.get().isCancelled()) {
            // Se cancelo justo entre medio: quien llama ya deberia haber chequeado
            // esto antes (ver EventAccessService#getEditableEvent), esto es la
            // defensa de la carrera, no el chequeo principal.
            throw new EventCancelledException(eventId);
        }
        // Un evento rechazado por un admin no se edita nunca (el rechazo es definitivo): misma regla que
        // EventAccessService.getEditableEvent(), que es la que usa el GET. Esto cierra el bypass por codigo.
        if (existing.get().isRejected()) {
            throw new EventRejectedException(eventId);
        }
        validateEventDate(dto);
        validateHasCategories(dto);
        // No alcanza con los confirmados: una invitacion sin contestar tambien tiene su lugar guardado (ver
        // EventDao.countOccupiedSpots).
        if (dto.getMaxCapacity() != null && dto.getMaxCapacity() < eventDao.countOccupiedSpots(eventId)) {
            throw new InvalidEventException(InvalidEventException.Reason.CAPACITY_BELOW_OCCUPIED, "maxCapacity");
        }
        // Si no se subio ninguna foto nueva, se mantiene la galeria que el
        // evento ya tenia; si se subio al menos una, las nuevas reemplazan toda
        // la galeria vieja. Subirlas aca (no en el controller) las deja dentro
        // de esta misma transaccion: si algo de abajo fallara, se deshacen
        // tambien, no quedan huerfanas.
        final boolean replacesGallery = !dto.getNewImages().isEmpty();
        // Se copian ya: la lista del evento cambia cuando el dao deja la galeria nueva.
        final List<Long> previousImageIds = new ArrayList<>(existing.get().getImageIds());
        final List<Long> imageIds = replacesGallery ? uploadImages(dto.getNewImages()) : previousImageIds;
        final Event updated;
        try {
            updated = eventDao.update(eventId, dto, imageIds).orElseThrow(() -> new EventNotFoundException(eventId));
        } catch (final DataIntegrityViolationException e) {
            throw new InvalidEventException(categoryConstraintViolated(e)
                    ? InvalidEventException.Reason.CATEGORY_NOT_FOUND : InvalidEventException.Reason.INVALID_DATA, e);
        }
        // Las fotos que ya no estan en la galeria se borran: si no, quedarian en la base (hasta 5 MB cada una) sin que
        // ningun evento las muestre.
        if (replacesGallery) {
            previousImageIds.forEach(imageService::delete);
        }
        // Si estaba en CHANGES_REQUESTED, este guardado ES el "reenviar a revision": vuelve solo a
        // PENDING, sin un boton aparte. No toca changes_requested_message (ver EventDao.resubmitForReview()).
        final boolean resubmitted = existing.get().isChangesRequested();
        if (resubmitted) {
            eventDao.resubmitForReview(eventId);
        }
        // Quienes escuchan este anuncio: RegistrantsEventListener, dentro de esta misma transaccion (bandeja de los
        // confirmados y hacer avanzar la lista de espera si subio el cupo), y MassNotifier, despues del commit y en
        // segundo plano (el mail a los confirmados, de a tandas).
        eventPublisher.publishEvent(new EventUpdatedEvent(eventId));
        return new EventUpdateOutcome(updated, resubmitted);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventCancelResult cancel(final long eventId) {
        // Con for update.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            throw new EventNotFoundException(eventId);
        }
        final Event event = existing.get();
        if (event.isCancelled()) {
            return EventCancelResult.ALREADY_CANCELLED;
        }
        if (event.isFinished()) {
            return EventCancelResult.FINISHED;
        }
        eventDao.cancel(eventId);
        // A los inscriptos les avisan quienes escuchan este anuncio: RegistrantsEventListener (la bandeja, dentro de
        // esta transaccion) y MassNotifier (el mail a los confirmados, despues del commit, en segundo plano y de a
        // tandas).
        eventPublisher.publishEvent(new EventCancelledEvent(eventId));
        return EventCancelResult.CANCELLED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventCancelResult removeByAdmin(final long eventId, final String reason) {
        // Con lock, igual que cancel(): si el creador cancelara el suyo justo en
        // el medio, no tiene que pasar que esto lo pise y mande el aviso de
        // "removido por un admin" sobre un evento que ya estaba cancelado.
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
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
        // cambio, recibe un aviso aparte y distinto: a el le interesa el motivo. (Quien escucha el anuncio: ver
        // cancel().)
        eventPublisher.publishEvent(new EventCancelledEvent(eventId));
        notificationService.notifyEventRemovedByAdmin(event, reason);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_REMOVED_BY_ADMIN, eventId, null, reason);
        return EventCancelResult.CANCELLED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventApprovalResult approve(final long eventId) {
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            return EventApprovalResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (!event.isPendingApproval()) {
            return EventApprovalResult.ALREADY_DECIDED;
        }
        eventDao.approve(eventId);
        notificationService.notifyEventApproved(event);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_APPROVED, eventId, null, null);
        notifyFollowersOfNewEvent(event);
        return EventApprovalResult.OK;
    }

    // A quien sigue a este organizador (ver FollowService), un aviso de que tiene un evento nuevo. Va aca
    // (no en create()) porque "nuevo" para el publico es recien cuando un admin lo aprueba, no cuando se
    // crea: antes de eso nadie fuera del creador y un admin puede verlo.
    private void notifyFollowersOfNewEvent(final Event event) {
        final Long organizerId = event.getCreatorId();
        if (organizerId == null) {
            return;
        }
        // La bandeja, todos juntos en una sola sentencia, dentro de esta transaccion. Los mails, en segundo plano y
        // despues del commit: MassNotifier escucha el anuncio y busca a los seguidores de a tandas. El nombre del
        // organizador se lee aca (con la sesion abierta) y viaja en el anuncio: el otro hilo no navega relaciones.
        inboxService.addForFollowersOfCreator(event.getId(), InboxMessageType.FOLLOWED_USER_NEW_EVENT);
        eventPublisher.publishEvent(new EventApprovedEvent(event.getId(), organizerId, event.getCreator().getName()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventApprovalResult reject(final long eventId, final String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > MAX_REJECT_REASON_LENGTH) {
            return EventApprovalResult.INVALID;
        }
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            return EventApprovalResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (!event.isPendingApproval()) {
            return EventApprovalResult.ALREADY_DECIDED;
        }
        eventDao.reject(eventId, reason);
        notificationService.notifyEventRejected(event, reason);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_REJECTED, eventId, null, reason);
        return EventApprovalResult.OK;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EventApprovalResult requestChanges(final long eventId, final String message) {
        if (message == null || message.trim().isEmpty() || message.length() > MAX_CHANGES_MESSAGE_LENGTH) {
            return EventApprovalResult.INVALID;
        }
        final Optional<Event> existing = eventDao.findByIdForUpdate(eventId);
        if (!existing.isPresent()) {
            return EventApprovalResult.NOT_FOUND;
        }
        final Event event = existing.get();
        if (!event.isPendingApproval()) {
            return EventApprovalResult.ALREADY_DECIDED;
        }
        eventDao.requestChanges(eventId, message);
        // Mismo mecanismo que ModerationService.requestChanges() para reportes: mail con link directo
        // a editar, mas el mensaje en la bandeja (reusa el mismo InboxMessageType -- "un admin te pidio
        // cambios" significa lo mismo aca que en el flujo de reportes).
        notificationService.notifyEventChangesRequested(event, message);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_CHANGES_REQUESTED, eventId, null, message);
        return EventApprovalResult.OK;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findMostRegistered(final int limit) {
        return eventDao.findMostRegistered(limit);
    }

    // La fecha no puede ser pasada ni estar a mas de MAX_YEARS_AHEAD años. El form (EventDateValidator) lo valida
    // para dar feedback; esto es la regla del service. Una fecha null la rechaza la base (NOT NULL).
    private static void validateEventDate(final EventDTO dto) {
        final Date eventDate = dto.getEventDate();
        if (eventDate == null) {
            return;
        }
        final Calendar limit = Calendar.getInstance();
        limit.add(Calendar.YEAR, MAX_YEARS_AHEAD);
        if (eventDate.before(new Date())) {
            throw new InvalidEventException(InvalidEventException.Reason.DATE_IN_PAST, "eventDate");
        }
        if (eventDate.after(limit.getTime())) {
            throw new InvalidEventException(InvalidEventException.Reason.DATE_TOO_FAR, "eventDate");
        }
    }

    // Un evento tiene al menos una categoria (ver tambien CategoriasValidator, que lo valida en el form).
    private static void validateHasCategories(final EventDTO dto) {
        if (dto.getCategories() == null || dto.getCategories().isEmpty()) {
            throw new InvalidEventException(InvalidEventException.Reason.NO_CATEGORY, "categories");
        }
    }

    // Las categorias elegidas viajan como ids (EventCategoryInfo) y las guarda el DAO junto con el evento. Una que
    // no existe (o una imagen que ya no esta, caso rarisimo: subida y borrada entre medio) viola una clave foranea
    // de event_category; para distinguirla de otra violacion se mira el nombre de la constraint en el mensaje
    // (event_category_...), que Postgres arma solo con esa convencion a partir del nombre de tabla y columna.
    private static boolean categoryConstraintViolated(final DataIntegrityViolationException e) {
        final Throwable cause = e.getMostSpecificCause();
        final String message = cause == null ? null : cause.getMessage();
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("event_category");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<Event> findByIdForUpdate(final long id) {
        return eventDao.findByIdForUpdate(id);
    }

    @Override
    @Transactional(readOnly = true)
    public long occupiedSpots(final long eventId) {
        return eventDao.countOccupiedSpots(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUpcomingPublicByCreator(final long creatorId, final Date now) {
        return eventDao.countUpcomingPublicByCreatorId(creatorId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPastPublicByCreator(final long creatorId, final Date now) {
        return eventDao.countPastPublicByCreatorId(creatorId, now);
    }

    // Sin eventos (total 0) no hay nada que pedirle a la base: ni la consulta de ids ni la de los eventos.
    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findUpcomingPublicByCreator(final long creatorId, final Date now, final int page,
                                                         final int pageSize, final long total) {
        final int effectivePage = PageResult.clampPage(page, total, pageSize);
        final List<Event> content = total == 0 ? Collections.<Event>emptyList()
                : eventDao.findUpcomingPublicByCreatorId(creatorId, now, effectivePage, pageSize);
        return new PageResult<>(content, total, effectivePage, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findPastPublicByCreator(final long creatorId, final Date now, final int page,
                                                     final int pageSize, final long total) {
        final int effectivePage = PageResult.clampPage(page, total, pageSize);
        final List<Event> content = total == 0 ? Collections.<Event>emptyList()
                : eventDao.findPastPublicByCreatorId(creatorId, now, effectivePage, pageSize);
        return new PageResult<>(content, total, effectivePage, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public long countMatching(final EventSearchCriteria criteria) {
        return eventDao.countSearch(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findMatching(final EventSearchCriteria criteria) {
        return eventDao.search(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findIdsNeedingReminder(final Date from, final Date until) {
        return eventDao.findIdsNeedingReminder(from, until);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markReminderSent(final long eventId, final Date when) {
        return eventDao.markReminderSent(eventId, when);
    }
}
