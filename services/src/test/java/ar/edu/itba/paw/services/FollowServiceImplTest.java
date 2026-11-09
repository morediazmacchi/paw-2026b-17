package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.FollowDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: el dao es un mock de Mockito (@Mock + when) y cada test asierta sobre
// lo que DEVUELVE el service (el contrato), sin verify.
@RunWith(MockitoJUnitRunner.class)
public class FollowServiceImplTest {

    private static final long FOLLOWER_ID = 1;
    private static final long FOLLOWED_ID = 2;

    @Mock
    private FollowDao followDao;

    private FollowServiceImpl followService;

    @Before
    public void setUp() {
        followService = new FollowServiceImpl(followDao);
    }

    @Test
    public void testFollowReturnsOk() {
        // Act
        final FollowResult result = followService.follow(FOLLOWER_ID, FOLLOWED_ID);

        // Assert
        assertEquals(FollowResult.OK, result);
    }

    @Test
    public void testFollowYourselfReturnsOwnProfile() {
        // Act
        final FollowResult result = followService.follow(FOLLOWER_ID, FOLLOWER_ID);

        // Assert
        assertEquals(FollowResult.OWN_PROFILE, result);
    }

    @Test
    public void testUnfollowReturnsWhatTheDaoReturns() {
        // Arrange
        when(followDao.unfollow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        // Act
        final boolean result = followService.unfollow(FOLLOWER_ID, FOLLOWED_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testUnfollowWhenNotFollowingReturnsFalse() {
        // Arrange
        when(followDao.unfollow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(false);

        // Act
        final boolean result = followService.unfollow(FOLLOWER_ID, FOLLOWED_ID);

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

    @Test
    public void testFindFollowersReturnsWhatTheDaoReturns() {
        // Arrange
        final User follower = new User();
        follower.setId(FOLLOWER_ID);
        final List<User> followers = Arrays.asList(follower);
        when(followDao.findFollowers(FOLLOWED_ID)).thenReturn(followers);

        // Act
        final List<User> result = followService.findFollowers(FOLLOWED_ID);

        // Assert
        assertEquals(followers, result);
    }
}
