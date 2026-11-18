package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.PublicUser;
import ar.edu.itba.paw.persistence.FollowDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private UserService userService;

    private FollowServiceImpl followService;

    @Before
    public void setUp() {
        followService = new FollowServiceImpl(followDao, userService);
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

    // Dos "seguir" a la vez: el segundo pasa el chequeo del DAO pero la base lo rechaza por la clave
    // (seguidor + seguido): SQLSTATE 23505. Para quien lo pidio es "ya lo seguia", no un error.
    @Test
    public void testFollowOrganizerOfReturnsFalseWhenTheDatabaseRejectsADuplicate() {
        // Arrange
        when(followDao.followOrganizerOf(FOLLOWER_ID, EVENT_ID)).thenThrow(new DataIntegrityViolationException(
                "clave seguidor+seguido", new SQLException("duplicado", "23505")));

        // Act
        final boolean result = followService.followOrganizerOf(EVENT_ID, FOLLOWER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testFollowOrganizerOfRethrowsADatabaseViolationThatIsNotTheDuplicate() {
        // Arrange: una clave foranea (SQLSTATE 23503) no es "ya lo seguia": no se disfraza.
        when(followDao.followOrganizerOf(FOLLOWER_ID, EVENT_ID)).thenThrow(new DataIntegrityViolationException("fk",
                new SQLException("fk", "23503")));

        // Act + Assert
        assertThrows(DataIntegrityViolationException.class, () -> followService.followOrganizerOf(EVENT_ID, FOLLOWER_ID));
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

    // ---- seguir / dejar de seguir desde el perfil publico ----

    @Test
    public void testFollowAnActivePersonFollowsThem() {
        // Arrange
        when(userService.findActivePublicById(FOLLOWED_ID))
                .thenReturn(Optional.of(new PublicUser(FOLLOWED_ID, "Ana", null, null)));
        when(followDao.follow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        // Act
        final boolean result = followService.follow(FOLLOWER_ID, FOLLOWED_ID);

        // Assert
        assertTrue(result);
    }

    // Uno no se sigue a si mismo: aunque el dao lo aceptara (senuelo), el service no lo pide.
    @Test
    public void testFollowYourselfReturnsFalse() {
        // Arrange
        lenient().when(userService.findActivePublicById(FOLLOWER_ID))
                .thenReturn(Optional.of(new PublicUser(FOLLOWER_ID, "Yo", null, null)));
        lenient().when(followDao.follow(FOLLOWER_ID, FOLLOWER_ID)).thenReturn(true);

        // Act
        final boolean result = followService.follow(FOLLOWER_ID, FOLLOWER_ID);

        // Assert
        assertFalse(result);
    }

    // Una cuenta suspendida o que no existe no se sigue (el perfil tampoco se muestra).
    @Test
    public void testFollowSomeoneWhoIsNotActiveReturnsFalse() {
        // Arrange
        when(userService.findActivePublicById(FOLLOWED_ID)).thenReturn(Optional.empty());
        lenient().when(followDao.follow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        // Act
        final boolean result = followService.follow(FOLLOWER_ID, FOLLOWED_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testFollowReturnsFalseWhenTheDatabaseRejectsADuplicate() {
        // Arrange
        when(userService.findActivePublicById(FOLLOWED_ID))
                .thenReturn(Optional.of(new PublicUser(FOLLOWED_ID, "Ana", null, null)));
        when(followDao.follow(FOLLOWER_ID, FOLLOWED_ID)).thenThrow(new DataIntegrityViolationException(
                "clave seguidor+seguido", new SQLException("duplicado", "23505")));

        // Act
        final boolean result = followService.follow(FOLLOWER_ID, FOLLOWED_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testUnfollowSomeoneYouFollowReturnsTrue() {
        // Arrange
        when(followDao.unfollow(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        // Act
        final boolean result = followService.unfollow(FOLLOWER_ID, FOLLOWED_ID);

        // Assert
        assertTrue(result);
    }
}
