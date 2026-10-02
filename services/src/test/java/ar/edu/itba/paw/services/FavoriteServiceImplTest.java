package ar.edu.itba.paw.services;

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
