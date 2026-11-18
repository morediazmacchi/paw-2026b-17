package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.persistence.FavoriteDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: el dao es un mock de Mockito (@Mock + when) y cada test
// asierta sobre lo que DEVUELVE el service (el contrato), sin verify.
@RunWith(MockitoJUnitRunner.class)
public class FavoriteServiceImplTest {

    private static final long USER_ID = 1;
    private static final long EVENT_ID = 2;

    @Mock
    private FavoriteDao favoriteDao;

    private FavoriteServiceImpl favoriteService;

    @Before
    public void setUp() {
        favoriteService = new FavoriteServiceImpl(favoriteDao);
    }

    @Test
    public void testAddReturnsTrueWhenTheEventWasAdded() {
        // Arrange
        when(favoriteDao.add(USER_ID, EVENT_ID)).thenReturn(true);

        // Act
        final boolean result = favoriteService.add(USER_ID, EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testAddReturnsFalseWhenTheEventWasAlreadyAFavorite() {
        // Arrange
        when(favoriteDao.add(USER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = favoriteService.add(USER_ID, EVENT_ID);

        // Assert
        assertFalse(result);
    }

    // Dos "guardar" a la vez: el segundo pasa el chequeo del DAO pero la base lo rechaza por la clave
    // (usuario + evento): SQLSTATE 23505. Para quien lo pidio es "ya era favorito", no un error.
    @Test
    public void testAddReturnsFalseWhenTheDatabaseRejectsADuplicate() {
        // Arrange
        when(favoriteDao.add(USER_ID, EVENT_ID)).thenThrow(new DataIntegrityViolationException("clave usuario+evento",
                new SQLException("duplicado", "23505")));

        // Act
        final boolean result = favoriteService.add(USER_ID, EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testAddRethrowsADatabaseViolationThatIsNotTheDuplicate() {
        // Arrange: una clave foranea (SQLSTATE 23503, el evento no existe) no es "ya era favorito": no se disfraza.
        when(favoriteDao.add(USER_ID, EVENT_ID)).thenThrow(new DataIntegrityViolationException("fk",
                new SQLException("fk", "23503")));

        // Act + Assert
        assertThrows(DataIntegrityViolationException.class, () -> favoriteService.add(USER_ID, EVENT_ID));
    }

    @Test
    public void testRemoveReturnsTrueWhenTheEventWasRemoved() {
        // Arrange
        when(favoriteDao.remove(USER_ID, EVENT_ID)).thenReturn(true);

        // Act
        final boolean result = favoriteService.remove(USER_ID, EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRemoveReturnsFalseWhenTheEventWasNotAFavorite() {
        // Arrange
        when(favoriteDao.remove(USER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = favoriteService.remove(USER_ID, EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testIsFavoriteReturnsTrueWhenTheEventIsAFavoriteOfTheUser() {
        // Arrange
        when(favoriteDao.isFavorite(USER_ID, EVENT_ID)).thenReturn(true);

        // Act
        final boolean result = favoriteService.isFavorite(USER_ID, EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsFavoriteReturnsFalseWhenTheEventIsNotAFavoriteOfTheUser() {
        // Arrange
        when(favoriteDao.isFavorite(USER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = favoriteService.isFavorite(USER_ID, EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testFindFavoriteEventIdsReturnsWhichOfTheGivenEventsAreFavorites() {
        // Arrange: el dao solo responde si le pasan los ids de ESA pagina
        final List<Long> pageIds = Arrays.asList(EVENT_ID, EVENT_ID + 1, EVENT_ID + 2);
        when(favoriteDao.findFavoriteEventIds(USER_ID, pageIds)).thenReturn(Collections.singleton(EVENT_ID + 1));

        // Act
        final Set<Long> result = favoriteService.findFavoriteEventIds(USER_ID, pageIds);

        // Assert
        assertEquals(Collections.singleton(EVENT_ID + 1), result);
    }

    @Test
    public void testFindFavoriteEventIdsIsEmptyWhenNoneOfTheEventsIsAFavorite() {
        // Arrange
        final List<Long> pageIds = Arrays.asList(EVENT_ID, EVENT_ID + 1);
        when(favoriteDao.findFavoriteEventIds(USER_ID, pageIds)).thenReturn(Collections.emptySet());

        // Act
        final Set<Long> result = favoriteService.findFavoriteEventIds(USER_ID, pageIds);

        // Assert
        assertTrue(result.isEmpty());
    }

    // ---- findViewablePage() / countViewable() ----

    private static Event eventWithId(final long id) {
        final Event event = new Event();
        event.setId(id);
        return event;
    }

    private static List<Long> idsOf(final PageResult<Event> page) {
        final List<Long> ids = new ArrayList<>();
        for (final Event event : page.getContent()) {
            ids.add(event.getId());
        }
        return ids;
    }

    // La base devuelve estos eventos, en este orden, como la pagina `page` (solo con ESA pagina responde: si el
    // service pidiera otra, el mock devuelve una lista vacia y el test falla).
    private void givenViewablePage(final int page, final long total, final Long... ids) {
        when(favoriteDao.countViewableByUserId(USER_ID)).thenReturn(total);
        final List<Event> events = new ArrayList<>();
        for (final Long id : ids) {
            events.add(eventWithId(id));
        }
        when(favoriteDao.findViewableEventsByUser(USER_ID, page, FavoriteServiceImpl.PAGE_SIZE)).thenReturn(events);
    }

    @Test
    public void testFindViewablePageReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 12 favoritos visibles, de a PAGE_SIZE (10) por pagina -> 2 paginas
        givenViewablePage(2, 12, 11L, 12L);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(USER_ID, false, 2);

        // Assert
        assertEquals(Arrays.asList(11L, 12L), idsOf(result));
        assertEquals(2, result.getPage());
        assertEquals(2, result.getTotalPages());
        assertEquals(12L, result.getTotalElements());
    }

    @Test
    public void testFindViewablePageKeepsTheOrderOfTheDatabase() {
        // Arrange: la base ordena 3, 1, 2 (mas recientes primero) y el service no los toca
        givenViewablePage(1, 3, 3L, 1L, 2L);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(USER_ID, false, 1);

        // Assert
        assertEquals(Arrays.asList(3L, 1L, 2L), idsOf(result));
    }

    @Test
    public void testFindViewablePageShowsTheLastPageWhenThePageIsBeyondIt() {
        // Arrange: 12 favoritos = 2 paginas; se pide la 99
        givenViewablePage(2, 12, 11L, 12L);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(USER_ID, false, 99);

        // Assert
        assertEquals(2, result.getPage());
        assertEquals(Arrays.asList(11L, 12L), idsOf(result));
    }

    @Test
    public void testFindViewablePageShowsTheFirstPageWhenThePageIsBelowOne() {
        // Arrange
        givenViewablePage(1, 12, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(USER_ID, false, 0);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(FavoriteServiceImpl.PAGE_SIZE, result.getContent().size());
    }

    @Test
    public void testFindViewablePageWithNoFavoritesReturnsAnEmptyFirstPage() {
        // Arrange
        when(favoriteDao.countViewableByUserId(USER_ID)).thenReturn(0L);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(USER_ID, false, 4);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(0, result.getTotalPages());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    public void testFindViewablePageForAnAdminListsAllTheirFavorites() {
        // Arrange: un admin ve todos; senuelo = lo que veria alguien que no es admin
        when(favoriteDao.countByUserId(USER_ID)).thenReturn(3L);
        when(favoriteDao.findEventsByUser(USER_ID, 1, FavoriteServiceImpl.PAGE_SIZE))
                .thenReturn(Arrays.asList(eventWithId(5), eventWithId(6), eventWithId(7)));
        lenient().when(favoriteDao.countViewableByUserId(USER_ID)).thenReturn(1L);
        lenient().when(favoriteDao.findViewableEventsByUser(USER_ID, 1, FavoriteServiceImpl.PAGE_SIZE))
                .thenReturn(Collections.singletonList(eventWithId(5)));

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(USER_ID, true, 1);

        // Assert
        assertEquals(Arrays.asList(5L, 6L, 7L), idsOf(result));
        assertEquals(3L, result.getTotalElements());
    }

    @Test
    public void testCountViewableForARegularUserCountsOnlyWhatTheyCanView() {
        // Arrange: el total de favoritos (senuelo) es mayor que los que puede ver
        when(favoriteDao.countViewableByUserId(USER_ID)).thenReturn(2L);
        lenient().when(favoriteDao.countByUserId(USER_ID)).thenReturn(4L);

        // Act
        final long result = favoriteService.countViewable(USER_ID, false);

        // Assert
        assertEquals(2L, result);
    }

    @Test
    public void testCountViewableForAnAdminCountsAllTheirFavorites() {
        // Arrange: un admin ve todos los eventos, no solo los que ve el resto
        when(favoriteDao.countByUserId(USER_ID)).thenReturn(4L);
        lenient().when(favoriteDao.countViewableByUserId(USER_ID)).thenReturn(2L);

        // Act
        final long result = favoriteService.countViewable(USER_ID, true);

        // Assert
        assertEquals(4L, result);
    }
}
