package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.FollowDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: el dao es un mock de Mockito (@Mock + when) y cada test asierta sobre
// lo que DEVUELVE el service (el contrato), sin verify.
@RunWith(MockitoJUnitRunner.class)
public class FollowServiceImplTest {

    private static final long FOLLOWER_ID = 1;
    // El organizador del evento EVENT_ID.
    private static final long FOLLOWED_ID = 2;
    private static final long EVENT_ID = 10;

    @Mock
    private FollowDao followDao;
    @Mock
    private EventDao eventDao;

    private FollowServiceImpl followService;

    @Before
    public void setUp() {
        followService = new FollowServiceImpl(followDao, eventDao);
    }

    private void eventOrganizedBy(final Long organizerId) {
        when(eventDao.findAccessById(EVENT_ID))
                .thenReturn(Optional.of(new EventAccessSummary(EVENT_ID, organizerId, false, true)));
    }

    // El dao solo responde true para (seguidor, organizador del evento): si el service siguiera a otra
    // persona (por ejemplo, al id del evento), el resultado seria false.
    @Test
    public void testFollowOrganizerOfFollowsTheOrganizerOfTheEvent() {
        // Arrange
        eventOrganizedBy(FOLLOWED_ID);
        when(followDao.follow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        // Act
        final boolean result = followService.followOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testFollowOrganizerOfWhenAlreadyFollowingReturnsFalse() {
        // Arrange
        eventOrganizedBy(FOLLOWED_ID);
        when(followDao.follow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(false);

        // Act
        final boolean result = followService.followOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertFalse(result);
    }

    // Evento viejo sin organizador cargado: no hay a quien seguir.
    @Test
    public void testFollowOrganizerOfAnEventWithoutOrganizerReturnsFalse() {
        // Arrange
        eventOrganizedBy(null);

        // Act
        final boolean result = followService.followOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testUnfollowOrganizerOfStopsFollowingTheOrganizerOfTheEvent() {
        // Arrange
        eventOrganizedBy(FOLLOWED_ID);
        when(followDao.unfollow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        // Act
        final boolean result = followService.unfollowOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testUnfollowOrganizerOfWhenNotFollowingReturnsFalse() {
        // Arrange
        eventOrganizedBy(FOLLOWED_ID);
        when(followDao.unfollow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(false);

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
