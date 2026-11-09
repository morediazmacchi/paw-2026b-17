package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.ImageDao;
import ar.edu.itba.paw.persistence.ReviewDao;
import ar.edu.itba.paw.persistence.UserDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito. Donde una rama no deberia consultar algo, el DAO tiene un dato senuelo (con
 * lenient()) que cambiaria el resultado si el service lo consultara igual.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserProfileServiceImplTest {

    private static final long USER_ID = 7;
    private static final long OTHER_ID = 8;
    private static final int PAGE_SIZE = UserProfileServiceImpl.PAGE_SIZE;

    @Mock
    private UserDao userDao;
    @Mock
    private EventDao eventDao;
    @Mock
    private ReviewDao reviewDao;
    @Mock
    private ImageDao imageDao;

    private UserProfileServiceImpl service;

    @Before
    public void setUp() {
        service = new UserProfileServiceImpl(userDao, eventDao, reviewDao, imageDao);
    }

    private static User activeUser() {
        final User user = ModelFixtures.user(USER_ID);
        user.setName("Tomas Rivas");
        user.setPassword("hash");
        return user;
    }

    private static List<Event> events(final long... ids) {
        final Event[] events = new Event[ids.length];
        for (int i = 0; i < ids.length; i++) {
            events[i] = ModelFixtures.event(ids[i]);
        }
        return Arrays.asList(events);
    }

    private void givenAnActiveUser() {
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
    }

    private void givenPastEvents(final long total) {
        when(eventDao.countPastPublicByCreatorId(eq(USER_ID), any(Date.class))).thenReturn(total);
    }

    @Test
    public void testFindProfileOfAUserThatDoesNotExistIsEmpty() {
        // Arrange: el DAO tiene eventos de ese id; si el service los consultara igual, no seria vacio
        when(userDao.findById(USER_ID)).thenReturn(Optional.empty());
        lenient().when(eventDao.countPastPublicByCreatorId(eq(USER_ID), any(Date.class))).thenReturn(3L);

        // Act
        final Optional<UserProfile> profile = service.findProfile(USER_ID, null, 1, 1);

        // Assert
        assertFalse(profile.isPresent());
    }

    @Test
    public void testFindProfileOfASuspendedUserIsEmpty() {
        // Arrange
        final User suspended = activeUser();
        suspended.setSuspended(true);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(suspended));
        lenient().when(eventDao.countPastPublicByCreatorId(eq(USER_ID), any(Date.class))).thenReturn(3L);

        // Act
        final Optional<UserProfile> profile = service.findProfile(USER_ID, null, 1, 1);

        // Assert
        assertFalse(profile.isPresent());
    }

    @Test
    public void testFindProfileOfAUserWhoNeverConfirmedTheMailIsEmpty() {
        // Arrange
        final User unverified = activeUser();
        unverified.setVerified(false);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(unverified));

        // Act
        final Optional<UserProfile> profile = service.findProfile(USER_ID, null, 1, 1);

        // Assert
        assertFalse(profile.isPresent());
    }

    @Test
    public void testFindProfileReturnsTheUpcomingEventsOfThePersonWithTheirTotal() {
        // Arrange
        givenAnActiveUser();
        final List<Event> upcoming = events(1, 2);
        when(eventDao.countUpcomingPublicByCreatorId(eq(USER_ID), any(Date.class))).thenReturn(2L);
        when(eventDao.findUpcomingPublicByCreatorId(eq(USER_ID), any(Date.class), eq(1), eq(PAGE_SIZE)))
                .thenReturn(upcoming);
        givenPastEvents(0);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertEquals(upcoming, profile.getUpcoming().getContent());
        assertEquals(2, profile.getUpcomingCount());
    }

    @Test
    public void testFindProfileReturnsThePastEventsAsTheOrganizedCount() {
        // Arrange
        givenAnActiveUser();
        final List<Event> past = events(5, 6, 7);
        givenPastEvents(3);
        when(eventDao.findPastPublicByCreatorId(eq(USER_ID), any(Date.class), eq(1), eq(PAGE_SIZE))).thenReturn(past);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertEquals(past, profile.getPast().getContent());
        assertEquals(3, profile.getOrganizedCount());
    }

    @Test
    public void testFindProfileAsksForTheLastPageOfAGroupWhenTheRequestedOneIsOutOfRange() {
        // Arrange: 25 eventos realizados = 3 paginas de 12; la pagina 99 cae en la 3
        givenAnActiveUser();
        final List<Event> lastPage = events(30);
        givenPastEvents(25);
        when(eventDao.findPastPublicByCreatorId(eq(USER_ID), any(Date.class), eq(3), eq(PAGE_SIZE)))
                .thenReturn(lastPage);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 99).get();

        // Assert
        assertEquals(3, profile.getPast().getPage());
        assertEquals(lastPage, profile.getPast().getContent());
    }

    @Test
    public void testFindProfileBringsTheAverageRatingAndTheReviewCountOfTheOrganizedEvents() {
        // Arrange
        givenAnActiveUser();
        givenPastEvents(2);
        when(reviewDao.countByCreatorId(USER_ID)).thenReturn(6L);
        when(reviewDao.findAverageRatingByCreatorId(USER_ID)).thenReturn(4.5);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertEquals(4.5, profile.getAverageRating(), 0.0001);
        assertEquals(6, profile.getReviewCount());
        assertTrue(profile.isHasReviews());
    }

    @Test
    public void testFindProfileWithoutReviewsHasAZeroAverage() {
        // Arrange: el DAO diria 4.5 si se le preguntara; sin reseñas no hay promedio que mostrar
        givenAnActiveUser();
        givenPastEvents(2);
        when(reviewDao.countByCreatorId(USER_ID)).thenReturn(0L);
        lenient().when(reviewDao.findAverageRatingByCreatorId(USER_ID)).thenReturn(4.5);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertEquals(0, profile.getAverageRating(), 0.0001);
        assertFalse(profile.isHasReviews());
    }

    @Test
    public void testAPersonWhoOrganizedTheMinimumNumberOfEventsIsASuperOrganizer() {
        // Arrange
        givenAnActiveUser();
        givenPastEvents(UserProfileService.SUPER_ORGANIZER_MIN_EVENTS);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertTrue(profile.isSuperOrganizer());
    }

    @Test
    public void testAPersonWithOneEventLessThanTheMinimumIsNotASuperOrganizer() {
        // Arrange
        givenAnActiveUser();
        givenPastEvents(UserProfileService.SUPER_ORGANIZER_MIN_EVENTS - 1);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertFalse(profile.isSuperOrganizer());
    }

    @Test
    public void testManyUpcomingEventsDoNotMakeASuperOrganizerOnTheirOwn() {
        // Arrange: solo cuentan los realizados, no los que todavia no pasaron
        givenAnActiveUser();
        when(eventDao.countUpcomingPublicByCreatorId(eq(USER_ID), any(Date.class))).thenReturn(40L);
        givenPastEvents(2);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertFalse(profile.isSuperOrganizer());
    }

    @Test
    public void testTheProfileIsOwnWhenTheViewerIsThatSamePerson() {
        // Arrange
        givenAnActiveUser();

        // Act
        final UserProfile profile = service.findProfile(USER_ID, USER_ID, 1, 1).get();

        // Assert
        assertTrue(profile.isOwn());
    }

    @Test
    public void testTheProfileIsNotOwnForAnotherViewer() {
        // Arrange
        givenAnActiveUser();

        // Act
        final UserProfile profile = service.findProfile(USER_ID, OTHER_ID, 1, 1).get();

        // Assert
        assertFalse(profile.isOwn());
    }

    @Test
    public void testTheProfileIsNotOwnForAnAnonymousViewer() {
        // Arrange
        givenAnActiveUser();

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertFalse(profile.isOwn());
    }

    @Test
    public void testFindProfileCarriesThePersonsNameAndBio() {
        // Arrange
        final User user = activeUser();
        user.setBio("Fotografo y productor");
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(user));

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, 1, 1).get();

        // Assert
        assertSame(user, profile.getUser());
        assertEquals(Collections.emptyList(), profile.getUpcoming().getContent());
    }

    // ---- findCoverImage() ----

    private static Image coverImage() {
        final Image image = new Image(new byte[] {1, 2, 3}, "image/png", "portada.png", 3);
        image.setId(33L);
        return image;
    }

    @Test
    public void testFindCoverImageReturnsTheImageTheUserChose() {
        // Arrange: solo el id de la portada del usuario devuelve la imagen
        final User user = activeUser();
        user.setCoverImageId(33L);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(user));
        when(imageDao.findById(33L)).thenReturn(Optional.of(coverImage()));

        // Act
        final Optional<Image> result = service.findCoverImage(USER_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(33L), result.get().getId());
    }

    @Test
    public void testFindCoverImageIsEmptyWhenTheUserChoseNone() {
        // Arrange: hay una imagen cualquiera en la base; si el service la devolviera igual, no estaria vacio
        givenAnActiveUser();
        lenient().when(imageDao.findById(anyLong())).thenReturn(Optional.of(coverImage()));

        // Act
        final Optional<Image> result = service.findCoverImage(USER_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindCoverImageOfASuspendedUserIsEmpty() {
        // Arrange
        final User suspended = activeUser();
        suspended.setCoverImageId(33L);
        suspended.setSuspended(true);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(suspended));
        lenient().when(imageDao.findById(33L)).thenReturn(Optional.of(coverImage()));

        // Act
        final Optional<Image> result = service.findCoverImage(USER_ID);

        // Assert
        assertFalse(result.isPresent());
    }
}
