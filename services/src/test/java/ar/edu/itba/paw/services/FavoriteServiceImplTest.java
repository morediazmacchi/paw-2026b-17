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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        favoriteService.add(USER_ID, EVENT_ID);

        verify(mockFavoriteDao).add(USER_ID, EVENT_ID);
    }

    @Test
    public void testRemoveDelegatesToTheDao() {
        favoriteService.remove(USER_ID, EVENT_ID);

        verify(mockFavoriteDao).remove(USER_ID, EVENT_ID);
    }

    @Test
    public void testIsFavoriteReturnsTrueWhenTheDaoSaysSo() {
        when(mockFavoriteDao.isFavorite(USER_ID, EVENT_ID)).thenReturn(true);

        assertTrue(favoriteService.isFavorite(USER_ID, EVENT_ID));
    }

    @Test
    public void testIsFavoriteReturnsFalseWhenTheDaoSaysSo() {
        when(mockFavoriteDao.isFavorite(USER_ID, EVENT_ID)).thenReturn(false);

        assertFalse(favoriteService.isFavorite(USER_ID, EVENT_ID));
    }

    @Test
    public void testFindEventIdsByUserDelegatesToTheDao() {
        final List<Long> ids = Collections.singletonList(EVENT_ID);
        when(mockFavoriteDao.findEventIdsByUser(USER_ID)).thenReturn(ids);

        assertEquals(ids, favoriteService.findEventIdsByUser(USER_ID));
    }
}
