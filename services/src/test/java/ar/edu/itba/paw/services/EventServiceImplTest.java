package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.InvalidEventException;
import ar.edu.itba.paw.persistence.CategoryDao;
import ar.edu.itba.paw.persistence.EventDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: EventDao, UserService y RegistrationService son @Mock.
 * Los comandos del dao (update, cancel, removeByAdmin, setFeatured) devuelven boolean: el
 * test los stubea con argumentos especificos (si el service no los invoca, Mockito falla por
 * stub sin usar) y asierta sobre lo que devuelve el service. Los avisos (mail e inbox) son
 * "disparar y olvidar" (void) y no se testean aca: verificar que se llamaron seria testear la
 * implementacion de EventServiceImpl, no el contrato de EventService.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long CATEGORY_ID = 7;
    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final long CREATOR_ID = 42;

    private static final long OTHER_ID = 43;

    @Mock
    private EventDao eventDao;
    @Mock
    private UserService userService;
    @Mock
    private CategoryDao categoryDao;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private InboxService inboxService;
    @Mock
    private ImageService imageService;
    @Mock
    private EventSimilarityService eventSimilarityService;

    private EventServiceImpl eventService;

    @Before
    public void setUp() {
        eventService = new EventServiceImpl(eventDao, userService, categoryDao, notificationService,
                registrationService, inboxService, imageService, eventSimilarityService);
        // El servicio busca las categorias elegidas entre las que existen: por defecto existe CATEGORY_ID.
        final List<Category> existingCategories = Collections.singletonList(ModelFixtures.category(CATEGORY_ID));
        lenient().when(categoryDao.findAll()).thenReturn(existingCategories);
    }

    private static Event someEvent() {
        return eventWith(EVENT_ID, CATEGORY_ID, CREATOR_ID);
    }

    private static Event eventWith(final long id, final long categoryId, final long creatorId) {
        final Event event = new Event();
        event.setId(id);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreator(ModelFixtures.user(creatorId));
        return event;
    }

    private static EventDTO someDto() {
        final EventDTO dto = new EventDTO();
        dto.setCategories(Collections.singletonList(new EventCategoryInfo(CATEGORY_ID)));
        dto.setCreatorEmail(CREATOR_EMAIL);
        dto.setCreatorId(CREATOR_ID);
        return dto;
    }

    private static List<Event> eventsWithIds(final int count) {
        final List<Event> events = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            events.add(eventWith(i, CATEGORY_ID, CREATOR_ID));
        }
        return events;
    }

    // El evento existe en el dao (con y sin lock, segun el metodo del service).
    private void existing(final Event event) {
        when(eventDao.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private void existingForUpdate(final Event event) {
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private static Event cancelledEvent() {
        final Event event = someEvent();
        event.setCancelledAt(new Date());
        return event;
    }

    // ---- create() ----

    @Test
    public void testCreateReturnsTheEventThatTheDaoPersisted() {
        // Arrange
        final Event persisted = someEvent();
        when(eventDao.create(any(Event.class)))
                .thenReturn(persisted);

        // Act
        final Event result = eventService.create(someDto());

        // Assert
        assertSame(persisted, result);
    }

    @Test
    public void testCreateWithInvalidCategoryThrowsInvalidEventExceptionWithCategoryKey() {
        // Arrange
        when(eventDao.create(any(Event.class)))
                .thenThrow(new DataIntegrityViolationException("violates foreign key constraint event_category_category_id_fkey"));

        // Act
        final InvalidEventException thrown = assertThrows(InvalidEventException.class,
                () -> eventService.create(someDto()));

        // Assert
        assertEquals("event.publish.categoryNotFound", thrown.getMessageKey());
    }

    @Test
    public void testCreateWithACategoryThatDoesNotExistThrowsInvalidEventExceptionWithCategoryKey() {
        // Arrange: el dto pide una categoria que no esta entre las que existen
        final EventDTO dto = someDto();
        dto.setCategories(Collections.singletonList(new EventCategoryInfo(CATEGORY_ID + 1)));

        // Act
        final InvalidEventException thrown = assertThrows(InvalidEventException.class,
                () -> eventService.create(dto));

        // Assert
        assertEquals("event.publish.categoryNotFound", thrown.getMessageKey());
    }

    @Test
    public void testCreateWithAnyOtherConstraintViolationThrowsInvalidEventExceptionWithGenericKey() {
        // Arrange
        when(eventDao.create(any(Event.class)))
                .thenThrow(new DataIntegrityViolationException("value too long for column title"));

        // Act
        final InvalidEventException thrown = assertThrows(InvalidEventException.class,
                () -> eventService.create(someDto()));

        // Assert
        assertEquals("event.publish.invalidData", thrown.getMessageKey());
    }

    @Test
    public void testCreateStillReturnsTheEventWhenCreatorLookupFindsNothing() {
        // Arrange
        // El creador ya viene resuelto (creatorId) desde el controller: esto
        // solo puede pasar si esa fila desapareciera justo entre medio. No
        // tiene que romper la creacion del evento, solo el nombre del mail.
        final Event persisted = someEvent();
        when(eventDao.create(any(Event.class))).thenReturn(persisted);
        when(userService.findById(CREATOR_ID)).thenReturn(Optional.empty());

        // Act
        final Event result = eventService.create(someDto());

        // Assert
        assertSame(persisted, result);
    }

    // ---- findById / findByIds / findAll / findByCategory / findByCreatorId ----

    @Test
    public void testFindByIdReturnsThePresentEvent() {
        // Arrange
        existing(someEvent());

        // Act
        final Optional<Event> result = eventService.findById(EVENT_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(EVENT_ID, result.get().getId().longValue());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheEventDoesNotExist() {
        // Arrange
        when(eventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Event> result = eventService.findById(EVENT_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindByIdsReturnsTheEventsTheDaoFinds() {
        // Arrange
        final List<Event> events = Arrays.asList(eventWith(1, CATEGORY_ID, CREATOR_ID), eventWith(3, CATEGORY_ID, CREATOR_ID));
        when(eventDao.findByIds(Arrays.asList(1L, 3L))).thenReturn(events);

        // Act
        final List<Event> result = eventService.findByIds(Arrays.asList(1L, 3L));

        // Assert
        assertEquals(events, result);
    }

    @Test
    public void testFindPageByCreatorIdReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 en total, de a EventServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<Event> pageTwo = Collections.singletonList(eventWith(1, CATEGORY_ID, CREATOR_ID));
        when(eventDao.countByCreatorId(CREATOR_ID)).thenReturn(25L);
        when(eventDao.findByCreatorId(CREATOR_ID, 2, EventServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<Event> result = eventService.findPageByCreatorId(CREATOR_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testFindPageByCreatorIdShowsTheLastPageWhenThePageIsBeyondIt() {
        // Arrange: 25 en total -> la ultima pagina es la 3
        final List<Event> lastPage = Collections.singletonList(eventWith(1, CATEGORY_ID, CREATOR_ID));
        when(eventDao.countByCreatorId(CREATOR_ID)).thenReturn(25L);
        when(eventDao.findByCreatorId(CREATOR_ID, 3, EventServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<Event> result = eventService.findPageByCreatorId(CREATOR_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testFindPageByCreatorIdShowsTheFirstPageWhenThePageIsBelowOne() {
        // Arrange
        final List<Event> firstPage = Collections.singletonList(eventWith(1, CATEGORY_ID, CREATOR_ID));
        when(eventDao.countByCreatorId(CREATOR_ID)).thenReturn(25L);
        when(eventDao.findByCreatorId(CREATOR_ID, 1, EventServiceImpl.PAGE_SIZE)).thenReturn(firstPage);

        // Act
        final PageResult<Event> result = eventService.findPageByCreatorId(CREATOR_ID, -3);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(firstPage, result.getContent());
    }

    @Test
    public void testFindPageByCreatorIdWithNoResultsReturnsAnEmptyFirstPage() {
        // Arrange
        when(eventDao.countByCreatorId(CREATOR_ID)).thenReturn(0L);
        when(eventDao.findByCreatorId(CREATOR_ID, 1, EventServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());

        // Act
        final PageResult<Event> result = eventService.findPageByCreatorId(CREATOR_ID, 5);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(0, result.getTotalPages());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    public void testCountByCreatorIdReturnsWhatTheDaoReturns() {
        // Arrange
        when(eventDao.countByCreatorId(CREATOR_ID)).thenReturn(2L);

        // Act
        final long result = eventService.countByCreatorId(CREATOR_ID);

        // Assert
        assertEquals(2L, result);
    }

    // ---- search() ----

    private static EventSearchCriteria criteria(final int page, final int pageSize) {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPage(page);
        criteria.setPageSize(pageSize);
        return criteria;
    }

    @Test
    public void testSearchReturnsTheRequestedPage() {
        // Arrange
        final EventSearchCriteria criteria = criteria(2, 10);
        when(eventDao.countSearch(criteria)).thenReturn(25L);

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertEquals(2, result.getPage());
    }

    @Test
    public void testSearchReturnsTheTotalNumberOfMatches() {
        // Arrange
        final EventSearchCriteria criteria = criteria(1, 10);
        when(eventDao.countSearch(criteria)).thenReturn(25L);
        when(eventDao.search(criteria)).thenReturn(eventsWithIds(10));

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertEquals(25L, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    public void testSearchWhenThePageIsBeyondTheLastFallsBackToTheLastPage() {
        // Arrange
        final EventSearchCriteria criteria = criteria(99, 10);
        when(eventDao.countSearch(criteria)).thenReturn(25L);

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertEquals(3, result.getPage());
    }

    @Test
    public void testSearchWhenThePageIsBelowOneFallsBackToTheFirstPage() {
        // Arrange
        final EventSearchCriteria criteria = criteria(0, 10);
        when(eventDao.countSearch(criteria)).thenReturn(25L);

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertEquals(1, result.getPage());
    }

    @Test
    public void testSearchWithoutResultsReturnsTheFirstPageEmpty() {
        // Arrange
        final EventSearchCriteria criteria = criteria(5, 10);
        when(eventDao.countSearch(criteria)).thenReturn(0L);

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertEquals(1, result.getPage());
        assertTrue(result.getContent().isEmpty());
    }

    // ---- update() ----

    // Caso feliz de update(): el evento es del que llama, sigue activo, y la capacidad nueva alcanza.
    private void updatable(final long occupied) {
        existingForUpdate(someEvent());
        when(registrationService.occupiedSpots(EVENT_ID)).thenReturn(occupied);
    }

    @Test
    public void testUpdateReturnsTheEventWithTheNewCapacity() {
        // Arrange
        updatable(5);
        final EventDTO dto = someDto();
        dto.setMaxCapacity(20);

        // Act
        final EventUpdateOutcome result = eventService.update(EVENT_ID, dto, CREATOR_ID);

        // Assert
        assertEquals(Integer.valueOf(20), result.getEvent().getMaxCapacity());
    }

    @Test
    public void testUpdateStoresTheChangesUnderTheEventId() {
        // Arrange: el Event que arma toEvent() a partir del dto llega sin id,
        // el service se lo tiene que poner.
        updatable(0);
        final EventDTO dto = someDto();
        dto.setMaxCapacity(20);

        // Act
        final EventUpdateOutcome result = eventService.update(EVENT_ID, dto, CREATOR_ID);

        // Assert
        assertEquals(EVENT_ID, result.getEvent().getId().longValue());
    }

    @Test(expected = EventNotFoundException.class)
    public void testUpdateWhenCallerIsNotTheCreatorThrowsEventNotFoundException() {
        // Arrange
        existingForUpdate(someEvent());

        // Act
        eventService.update(EVENT_ID, someDto(), OTHER_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventNotFoundException.class)
    public void testUpdateWhenTheEventDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        eventService.update(EVENT_ID, someDto(), CREATOR_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventCancelledException.class)
    public void testUpdateWhenTheEventIsCancelledThrowsEventCancelledException() {
        // Arrange
        existingForUpdate(cancelledEvent());

        // Act
        eventService.update(EVENT_ID, someDto(), CREATOR_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testUpdateWhenNewCapacityIsBelowOccupiedThrowsInvalidEventExceptionOnMaxCapacity() {
        // Arrange
        updatable(5);
        final EventDTO dto = someDto();
        dto.setMaxCapacity(3);

        // Act
        final InvalidEventException thrown = assertThrows(InvalidEventException.class,
                () -> eventService.update(EVENT_ID, dto, CREATOR_ID));

        // Assert
        assertEquals("cupo.muyBajo", thrown.getMessageKey());
        assertEquals("maxCapacity", thrown.getField());
    }

    @Test
    public void testUpdateWhenNewCapacityEqualsOccupiedSucceeds() {
        // Arrange
        updatable(5);
        final EventDTO dto = someDto();
        dto.setMaxCapacity(5);

        // Act
        final EventUpdateOutcome result = eventService.update(EVENT_ID, dto, CREATOR_ID);

        // Assert: el limite exacto (cupo == ocupados) es valido y el cupo nuevo queda guardado.
        assertEquals(Integer.valueOf(5), result.getEvent().getMaxCapacity());
    }

    @Test
    public void testUpdateWhenNewCapacityIsNullSucceedsEvenIfSomeSpotsAreOccupied() {
        // Arrange
        // Sin cupo (ilimitado) no hay nada que comparar contra lo ocupado: tiene
        // que actualizar igual aunque haya lugares ocupados.
        existingForUpdate(someEvent());
        final EventDTO dto = someDto();
        dto.setMaxCapacity(null);

        // Act
        final EventUpdateOutcome result = eventService.update(EVENT_ID, dto, CREATOR_ID);

        // Assert: se guarda sin cupo (ilimitado).
        assertNull(result.getEvent().getMaxCapacity());
    }

    // Si el organizador sube el cupo, los primeros de la lista de espera entran ya.
    @Test
    public void testUpdatePromotesTheWaitlistSoRaisedCapacityIsFilled() {
        // Arrange
        updatable(0);
        final EventDTO dto = someDto();
        dto.setMaxCapacity(20);
        when(registrationService.promoteFromWaitlist(EVENT_ID)).thenReturn(2);

        // Act
        final EventUpdateOutcome result = eventService.update(EVENT_ID, dto, CREATOR_ID);

        // Assert
        assertEquals(2, result.getPromotedFromWaitlist());
    }

    // ---- cancel() ----

    @Test
    public void testCancelSucceedsAndReturnsCancelled() {
        // Arrange
        existingForUpdate(someEvent());
        when(eventDao.cancel(EVENT_ID)).thenReturn(true);

        // Act
        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(EventCancelResult.CANCELLED, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testCancelWhenCallerIsNotTheCreatorThrowsEventNotFoundException() {
        // Arrange
        existingForUpdate(someEvent());

        // Act
        eventService.cancel(EVENT_ID, OTHER_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventNotFoundException.class)
    public void testCancelWhenTheEventDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        eventService.cancel(EVENT_ID, CREATOR_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testCancelWhenAlreadyCancelledReturnsAlreadyCancelled() {
        // Arrange
        existingForUpdate(cancelledEvent());

        // Act
        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(EventCancelResult.ALREADY_CANCELLED, result);
    }

    // ---- removeByAdmin() (moderacion) ----

    @Test
    public void testRemoveByAdminSucceedsRegardlessOfWhoCreatedTheEvent() {
        // Arrange
        // removeByAdmin() y cancel() devuelven el mismo CANCELLED: lo que los distingue es el
        // comando del dao que ejecutan (removeByAdmin guarda la marca "retirado por un admin" y el motivo).
        existingForUpdate(someEvent());
        when(eventDao.removeByAdmin(EVENT_ID, "Contenido inapropiado")).thenReturn(true);

        // Act
        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "Contenido inapropiado");

        // Assert
        assertEquals(EventCancelResult.CANCELLED, result);
    }

    @Test
    public void testRemoveByAdminWhenTheEventDoesNotExistReturnsNotFound() {
        // Arrange
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "motivo");

        // Assert
        assertEquals(EventCancelResult.NOT_FOUND, result);
    }

    @Test
    public void testRemoveByAdminWhenAlreadyCancelledReturnsAlreadyCancelled() {
        // Arrange
        existingForUpdate(cancelledEvent());

        // Act
        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "motivo");

        // Assert
        assertEquals(EventCancelResult.ALREADY_CANCELLED, result);
    }

    // ---- approve() / reject() (revision previa a publicarse, ver EventApprovalStatus) ----

    @Test
    public void testApproveSucceedsOnAPendingEvent() {
        // Arrange: someEvent() arranca PENDING (default de Event.approvalStatus)
        // y su creador es CREATOR_ID, asi que el admin que aprueba tiene que ser otro.
        existingForUpdate(someEvent());
        when(eventDao.approve(EVENT_ID)).thenReturn(true);

        // Act
        final EventApprovalResult result = eventService.approve(EVENT_ID, OTHER_ID);

        // Assert
        assertEquals(EventApprovalResult.OK, result);
    }

    @Test
    public void testApproveWhenTheEventDoesNotExistReturnsNotFound() {
        // Arrange
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventApprovalResult result = eventService.approve(EVENT_ID, OTHER_ID);

        // Assert
        assertEquals(EventApprovalResult.NOT_FOUND, result);
    }

    @Test
    public void testApproveWhenAlreadyApprovedReturnsAlreadyDecided() {
        // Arrange
        final Event event = someEvent();
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        existingForUpdate(event);

        // Act
        final EventApprovalResult result = eventService.approve(EVENT_ID, OTHER_ID);

        // Assert
        assertEquals(EventApprovalResult.ALREADY_DECIDED, result);
    }

    @Test
    public void testApproveWhenAlreadyRejectedReturnsAlreadyDecided() {
        // Arrange
        final Event event = someEvent();
        event.setApprovalStatus(EventApprovalStatus.REJECTED);
        existingForUpdate(event);

        // Act
        final EventApprovalResult result = eventService.approve(EVENT_ID, OTHER_ID);

        // Assert
        assertEquals(EventApprovalResult.ALREADY_DECIDED, result);
    }

    // Mismo conflicto de intereses que ModerationServiceImplTest con OWN_EVENT.
    @Test
    public void testApproveOnOwnEventReturnsOwnEvent() {
        // Arrange: someEvent() tiene como creador a CREATOR_ID.
        existingForUpdate(someEvent());

        // Act
        final EventApprovalResult result = eventService.approve(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(EventApprovalResult.OWN_EVENT, result);
    }

    @Test
    public void testRejectSucceedsOnAPendingEvent() {
        // Arrange
        existingForUpdate(someEvent());
        when(eventDao.reject(EVENT_ID, "Contenido inapropiado")).thenReturn(true);

        // Act
        final EventApprovalResult result = eventService.reject(EVENT_ID, "Contenido inapropiado", OTHER_ID);

        // Assert
        assertEquals(EventApprovalResult.OK, result);
    }

    @Test
    public void testRejectWhenTheEventDoesNotExistReturnsNotFound() {
        // Arrange
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventApprovalResult result = eventService.reject(EVENT_ID, "motivo", OTHER_ID);

        // Assert
        assertEquals(EventApprovalResult.NOT_FOUND, result);
    }

    @Test
    public void testRejectWhenAlreadyDecidedReturnsAlreadyDecided() {
        // Arrange
        final Event event = someEvent();
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        existingForUpdate(event);

        // Act
        final EventApprovalResult result = eventService.reject(EVENT_ID, "motivo", OTHER_ID);

        // Assert
        assertEquals(EventApprovalResult.ALREADY_DECIDED, result);
    }

    @Test
    public void testRejectOnOwnEventReturnsOwnEvent() {
        // Arrange
        existingForUpdate(someEvent());

        // Act
        final EventApprovalResult result = eventService.reject(EVENT_ID, "motivo", CREATOR_ID);

        // Assert
        assertEquals(EventApprovalResult.OWN_EVENT, result);
    }

    // ---- findFeatured() / setFeatured() (banner de /eventos) ----

    @Test
    public void testFindFeaturedReturnsTheFeaturedEventsTheDaoFinds() {
        // Arrange
        final List<Event> featured = Arrays.asList(eventWith(2, CATEGORY_ID, CREATOR_ID), eventWith(3, CATEGORY_ID, CREATOR_ID));
        when(eventDao.findFeatured(2)).thenReturn(featured);

        // Act
        final List<Event> result = eventService.findFeatured(2);

        // Assert
        assertEquals(featured, result);
    }

    // ---- search() con RECOMENDADOS ----

    @Test
    public void testSearchRecommendedWithAViewerIsRankedByTheSimilarityService() {
        // Arrange
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setSort(EventSort.RECOMENDADOS);
        criteria.setViewerId(CREATOR_ID);
        final PageResult<Event> ranked = new PageResult<>(eventsWithIds(2), 2, 1, EventServiceImpl.PAGE_SIZE);
        when(eventSimilarityService.searchRecommended(criteria)).thenReturn(ranked);

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertSame(ranked, result);
        assertEquals(EventServiceImpl.PAGE_SIZE, criteria.getPageSize());
    }

    @Test
    public void testSearchRecommendedWithoutAViewerFallsBackToTheDaoOrder() {
        // Arrange: sin sesion no hay a quien recomendarle: es una busqueda comun (el dao ordena por fecha)
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setSort(EventSort.RECOMENDADOS);
        final List<Event> events = eventsWithIds(2);
        when(eventDao.countSearch(criteria)).thenReturn(2L);
        when(eventDao.search(criteria)).thenReturn(events);
        // Si el service de similitud se usara igual, el resultado seria este otro.
        lenient().when(eventSimilarityService.searchRecommended(criteria))
                .thenReturn(new PageResult<>(eventsWithIds(5), 5, 1, EventServiceImpl.PAGE_SIZE));

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertEquals(events, result.getContent());
    }

    @Test
    public void testSearchByAnotherSortDoesNotUseTheSimilarityServiceEvenWithAViewer() {
        // Arrange
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setSort(EventSort.PRECIO_ASC);
        criteria.setViewerId(CREATOR_ID);
        when(eventDao.countSearch(criteria)).thenReturn(0L);
        when(eventDao.search(criteria)).thenReturn(Collections.emptyList());
        // Si el service de similitud se usara igual, el resultado no estaria vacio.
        lenient().when(eventSimilarityService.searchRecommended(criteria))
                .thenReturn(new PageResult<>(eventsWithIds(5), 5, 1, EventServiceImpl.PAGE_SIZE));

        // Act
        final PageResult<Event> result = eventService.search(criteria);

        // Assert
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    public void testSetFeaturedTrueReturnsTrueWhenTheEventWasMarked() {
        // Arrange
        when(eventDao.setFeatured(EVENT_ID, true)).thenReturn(true);

        // Act
        final boolean result = eventService.setFeatured(EVENT_ID, true);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSetFeaturedFalseReturnsTrueWhenTheEventWasUnmarked() {
        // Arrange
        when(eventDao.setFeatured(EVENT_ID, false)).thenReturn(true);

        // Act
        final boolean result = eventService.setFeatured(EVENT_ID, false);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSetFeaturedReturnsFalseWhenTheEventDoesNotExist() {
        // Arrange
        when(eventDao.setFeatured(EVENT_ID, true)).thenReturn(false);

        // Act
        final boolean result = eventService.setFeatured(EVENT_ID, true);

        // Assert
        assertFalse(result);
    }
}
