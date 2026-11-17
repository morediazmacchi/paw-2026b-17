package ar.edu.itba.paw.services;

import ar.edu.itba.paw.persistence.FollowDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: el dao es un mock de Mockito (@Mock + when) y cada test asierta sobre
// lo que DEVUELVE el service (el contrato), sin verify.
@RunWith(MockitoJUnitRunner.class)
public class FollowServiceImplTest {

    private static final long FOLLOWER_ID = 1;
    // La persona que es seguida (isFollowing / countFollowers hablan de personas, no de eventos).
    private static final long FOLLOWED_ID = 2;
    private static final long EVENT_ID = 10;

    @Mock
    private FollowDao followDao;

    private FollowServiceImpl followService;

    @Before
    public void setUp() {
        followService = new FollowServiceImpl(followDao);
    }

    // El dao solo responde para (seguidor, EVENTO): si el service le pasara otro id, el resultado seria false.
    @Test
    public void testFollowOrganizerOfFollowsTheOrganizerOfTheEvent() {
        // Arrange
        when(followDao.followOrganizerOf(FOLLOWER_ID, EVENT_ID)).thenReturn(true);

        // Act
        final boolean result = followService.followOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testFollowOrganizerOfWhenAlreadyFollowingReturnsFalse() {
        // Arrange
        when(followDao.followOrganizerOf(FOLLOWER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = followService.followOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testUnfollowOrganizerOfStopsFollowingTheOrganizerOfTheEvent() {
        // Arrange
        when(followDao.unfollowOrganizerOf(FOLLOWER_ID, EVENT_ID)).thenReturn(true);

        // Act
        final boolean result = followService.unfollowOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testUnfollowOrganizerOfWhenNotFollowingReturnsFalse() {
        // Arrange
        when(followDao.unfollowOrganizerOf(FOLLOWER_ID, EVENT_ID)).thenReturn(false);

        // Act
        final boolean result = followService.unfollowOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testIsFollowingReturnsWhatTheDaoReturns() {
        // Arrange
        when(followDao.isFollowing(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        // Act
        final boolean result = followService.isFollowing(FOLLOWER_ID, FOLLOWED_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCountFollowersReturnsWhatTheDaoReturns() {
        // Arrange
        when(followDao.countFollowers(FOLLOWED_ID)).thenReturn(3L);

        // Act
        final long result = followService.countFollowers(FOLLOWED_ID);

        // Assert
        assertEquals(3L, result);
    }
}
