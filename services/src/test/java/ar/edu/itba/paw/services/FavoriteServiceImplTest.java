package ar.edu.itba.paw.services;

import ar.edu.itba.paw.persistence.FavoriteDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: "es recomendable NO utilizar el metodo verify,
// pues queremos verificar comportamiento, no la implementacion". Para add()/
// remove() (void, sin nada que devolver) el comportamiento es justamente
// "que le llegue esto al dao" -- se captura con doAnswer() sobre el stub y se
// comprueba con un assert comun, nunca con Mockito.verify().
@RunWith(MockitoJUnitRunner.class)
public class FavoriteServiceImplTest {

    private static final long USER_ID = 1;
    private static final long EVENT_ID = 2;

    @Mock
    private FavoriteDao mockFavoriteDao;

    @InjectMocks
    private FavoriteServiceImpl favoriteService;

    @Test
    public void testAddDelegatesToTheDao() {
        // Arrange
        final long[] calledWith = new long[2];
        doAnswer(invocation -> {
            calledWith[0] = invocation.getArgument(0);
            calledWith[1] = invocation.getArgument(1);
            return null;
        }).when(mockFavoriteDao).add(anyLong(), anyLong());

        // Act
        favoriteService.add(USER_ID, EVENT_ID);

        // Assert
        assertEquals(USER_ID, calledWith[0]);
        assertEquals(EVENT_ID, calledWith[1]);
    }

    @Test
    public void testRemoveDelegatesToTheDao() {
        // Arrange
        final long[] calledWith = new long[2];
        doAnswer(invocation -> {
            calledWith[0] = invocation.getArgument(0);
            calledWith[1] = invocation.getArgument(1);
            return null;
        }).when(mockFavoriteDao).remove(anyLong(), anyLong());

        // Act
        favoriteService.remove(USER_ID, EVENT_ID);

        // Assert
        assertEquals(USER_ID, calledWith[0]);
        assertEquals(EVENT_ID, calledWith[1]);
    }

    @Test
    public void testIsFavoriteReturnsTrueWhenTheDaoSaysSo() {
        // Arrange
        when(mockFavoriteDao.isFavorite(USER_ID, EVENT_ID)).thenReturn(true);

        // Act
        final boolean result = favoriteService.isFavorite(USER_ID, EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsFavoriteReturnsFalseWhenTheDaoSaysSo() {
        // Arrange
        when(mockFavoriteDao.isFavorite(USER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = favoriteService.isFavorite(USER_ID, EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testFindEventIdsByUserDelegatesToTheDao() {
        // Arrange
        final List<Long> ids = Collections.singletonList(EVENT_ID);
        when(mockFavoriteDao.findEventIdsByUser(USER_ID)).thenReturn(ids);

        // Act
        final List<Long> result = favoriteService.findEventIdsByUser(USER_ID);

        // Assert
        assertEquals(ids, result);
    }
}
