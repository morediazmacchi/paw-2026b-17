package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.persistence.FavoriteDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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

    private FavoriteServiceImpl favoriteService;

    @Before
    public void setUp() {
        favoriteService = new FavoriteServiceImpl(favoriteDao, accessChecker);
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
        final Event event = new Event();
        event.setId(EVENT_ID);
        when(accessChecker.getViewableEvent(EVENT_ID, user)).thenReturn(event);
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
        final Event event = new Event();
        event.setId(EVENT_ID);
        when(accessChecker.getViewableEvent(EVENT_ID, user)).thenReturn(event);
        when(favoriteDao.add(USER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = favoriteService.add(user, EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testAddThrowsEventNotFoundExceptionWhenTheUserCannotViewTheEvent() {
        // Arrange
        final User user = someUser();
        when(accessChecker.getViewableEvent(EVENT_ID, user)).thenThrow(new EventNotFoundException(EVENT_ID));

        // Act & Assert
        assertThrows(EventNotFoundException.class, () -> favoriteService.add(user, EVENT_ID));
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
}
