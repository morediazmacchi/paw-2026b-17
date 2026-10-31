package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.FavoriteDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: el dao es un mock de Mockito (@Mock + when) y cada test
// asierta sobre lo que DEVUELVE el service (el contrato), sin verify.
@RunWith(MockitoJUnitRunner.class)
public class FavoriteServiceImplTest {

    private static final long USER_ID = 1;
    private static final long EVENT_ID = 2;

    @Mock
    private FavoriteDao favoriteDao;
    @Mock
    private EventAccessService accessChecker;
    @Mock
    private EventService eventService;

    private FavoriteServiceImpl favoriteService;

    @Before
    public void setUp() {
        favoriteService = new FavoriteServiceImpl(favoriteDao, accessChecker, eventService);
    }

    private static User someUser() {
        final User user = new User("test@mail.com", "Ana");
        user.setId(USER_ID);
        return user;
    }

    @Test
    public void testAddReturnsTrueWhenTheEventWasAdded() {
        // Arrange: User no tiene equals(), asi que se arma una sola instancia y se
        // reusa -- dos someUser() serian "distintos" para el stub de Mockito.
        final User user = someUser();
        when(favoriteDao.add(USER_ID, EVENT_ID)).thenReturn(true);

        // Act
        final boolean result = favoriteService.add(user, EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testAddReturnsFalseWhenTheEventWasAlreadyAFavorite() {
        // Arrange
        final User user = someUser();
        when(favoriteDao.add(USER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = favoriteService.add(user, EVENT_ID);

        // Assert
        assertFalse(result);
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
    public void testFindEventIdsByUserReturnsThatUsersFavorites() {
        // Arrange
        when(favoriteDao.findEventIdsByUser(USER_ID)).thenReturn(Arrays.asList(EVENT_ID, EVENT_ID + 1));

        // Act
        final List<Long> result = favoriteService.findEventIdsByUser(USER_ID);

        // Assert
        assertEquals(Arrays.asList(EVENT_ID, EVENT_ID + 1), result);
    }

    @Test
    public void testFindEventIdsByUserReturnsEmptyWhenTheUserHasNoFavorites() {
        // Arrange
        when(favoriteDao.findEventIdsByUser(USER_ID)).thenReturn(Collections.emptyList());

        // Act
        final List<Long> result = favoriteService.findEventIdsByUser(USER_ID);

        // Assert
        assertEquals(Collections.emptyList(), result);
    }

    // ---- findViewablePage() / countViewable() ----

    private static Event eventWithId(final long id) {
        final Event event = new Event();
        event.setId(id);
        return event;
    }

    // Los favoritos 1..count del usuario, mas recientes primero. Los ids de
    // "hidden" son de eventos privados a los que ya no puede entrar; el resto
    // los ve. Devuelve los eventos tal como los devuelve eventService.findByIds().
    private void givenFavorites(final User user, final int count, final long... hidden) {
        final List<Long> favoriteIds = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        for (long id = 1; id <= count; id++) {
            favoriteIds.add(id);
            final Event event = eventWithId(id);
            events.add(event);
            boolean visible = true;
            for (final long hiddenId : hidden) {
                visible = visible && hiddenId != id;
            }
            when(accessChecker.canView(event, user)).thenReturn(visible);
        }
        when(favoriteDao.findEventIdsByUser(USER_ID)).thenReturn(favoriteIds);
        when(eventService.findByIds(favoriteIds)).thenReturn(events);
    }

    private static List<Long> idsOf(final PageResult<Event> page) {
        final List<Long> ids = new ArrayList<>();
        for (final Event event : page.getContent()) {
            ids.add(event.getId());
        }
        return ids;
    }

    @Test
    public void testFindViewablePageReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 12 favoritos visibles, de a PAGE_SIZE (10) por pagina -> 2 paginas
        final User user = someUser();
        givenFavorites(user, 12);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(user, 2);

        // Assert
        assertEquals(Arrays.asList(11L, 12L), idsOf(result));
        assertEquals(2, result.getPage());
        assertEquals(2, result.getTotalPages());
        assertEquals(12L, result.getTotalElements());
    }

    @Test
    public void testFindViewablePageDoesNotCountFavoritesTheUserCanNoLongerView() {
        // Arrange: el 2 es de un evento privado sin acceso
        final User user = someUser();
        givenFavorites(user, 3, 2L);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(user, 1);

        // Assert
        assertEquals(Arrays.asList(1L, 3L), idsOf(result));
        assertEquals(2L, result.getTotalElements());
    }

    @Test
    public void testFindViewablePageFillsTheFirstPageEvenWhenAHiddenFavoriteWouldHaveBeenOnIt() {
        // Arrange: 11 favoritos y el 5 oculto -> 10 visibles = una pagina llena, sin pagina 2
        final User user = someUser();
        givenFavorites(user, 11, 5L);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(user, 1);

        // Assert
        assertEquals(FavoriteServiceImpl.PAGE_SIZE, result.getContent().size());
        assertEquals(1, result.getTotalPages());
    }

    @Test
    public void testFindViewablePageShowsTheLastPageWhenThePageIsBeyondIt() {
        // Arrange
        final User user = someUser();
        givenFavorites(user, 12);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(user, 99);

        // Assert
        assertEquals(2, result.getPage());
        assertEquals(Arrays.asList(11L, 12L), idsOf(result));
    }

    @Test
    public void testFindViewablePageShowsTheFirstPageWhenThePageIsBelowOne() {
        // Arrange
        final User user = someUser();
        givenFavorites(user, 12);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(user, 0);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(FavoriteServiceImpl.PAGE_SIZE, result.getContent().size());
    }

    @Test
    public void testFindViewablePageWithNoFavoritesReturnsAnEmptyFirstPage() {
        // Arrange
        when(favoriteDao.findEventIdsByUser(USER_ID)).thenReturn(Collections.emptyList());

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(someUser(), 4);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(0, result.getTotalPages());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    public void testFindViewablePageSkipsAFavoriteWhoseEventNoLongerExists() {
        // Arrange: el favorito 2 no vuelve en findByIds()
        final User user = someUser();
        final Event first = eventWithId(1);
        when(favoriteDao.findEventIdsByUser(USER_ID)).thenReturn(Arrays.asList(1L, 2L));
        when(eventService.findByIds(Arrays.asList(1L, 2L))).thenReturn(Collections.singletonList(first));
        when(accessChecker.canView(first, user)).thenReturn(true);

        // Act
        final PageResult<Event> result = favoriteService.findViewablePage(user, 1);

        // Assert
        assertEquals(Collections.singletonList(1L), idsOf(result));
    }

    @Test
    public void testCountViewableCountsOnlyTheFavoritesTheUserCanView() {
        // Arrange
        final User user = someUser();
        givenFavorites(user, 4, 1L, 3L);

        // Act
        final long result = favoriteService.countViewable(user);

        // Assert
        assertEquals(2L, result);
    }
}
