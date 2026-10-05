package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.ReviewDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: ReviewDao, EventDao y RegistrationService son @Mock
 * y cada test asierta sobre lo que devuelve el service (sin verify).
 */
@RunWith(MockitoJUnitRunner.class)
public class ReviewServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long USER_ID = 2;
    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final long CREATOR_ID = 20;
    private static final String USER_EMAIL = "asistente@mail.com";
    private static final int RATING = 4;
    private static final String COMMENT = "Buen evento";

    @Mock
    private ReviewDao reviewDao;
    @Mock
    private EventDao eventDao;
    @Mock
    private RegistrationService registrationService;

    private ReviewServiceImpl reviewService;

    @Before
    public void setUp() {
        reviewService = new ReviewServiceImpl(reviewDao, eventDao, registrationService);
    }

    private static User someUser() {
        final User user = new User(USER_EMAIL, "Ana");
        user.setId(USER_ID);
        return user;
    }

    private static Event pastEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        event.setEventDate(new Date(System.currentTimeMillis() - 86_400_000L)); // ayer
        return event;
    }

    private static Event futureEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setEventDate(new Date(System.currentTimeMillis() + 86_400_000L)); // mañana
        return event;
    }

    // La inscripcion de someUser() al evento 1: findByEventAndUserId la busca por userId.
    private static Registration registrationOfTheUser(final RegistrationStatus status) {
        final Registration registration = new Registration(EVENT_ID, USER_EMAIL, "Ana", status);
        registration.setUserId(USER_ID);
        return registration;
    }

    // ---- findByEventAndUser() ----

    @Test
    public void testFindByEventAndUserReturnsTheReviewTheUserLeftForThatEvent() {
        // Arrange
        final Review review = new Review(EVENT_ID, USER_ID, RATING, COMMENT);
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.of(review));

        // Act
        final Optional<Review> result = reviewService.findByEventAndUser(EVENT_ID, USER_ID);

        // Assert
        assertTrue(result.isPresent());
        assertSame(review, result.get());
    }

    @Test
    public void testFindByEventAndUserReturnsEmptyWhenTheUserLeftNoReview() {
        // Arrange
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Review> result = reviewService.findByEventAndUser(EVENT_ID, USER_ID);

        // Assert
        assertEquals(Optional.empty(), result);
    }

    // ---- findByEventsAndUser() ----

    @Test
    public void testFindByEventsAndUserReturnsWhatTheDaoReturns() {
        // Arrange
        final List<Review> reviews = Collections.singletonList(new Review(EVENT_ID, USER_ID, RATING, COMMENT));
        when(reviewDao.findByEventsAndUser(Collections.singletonList(EVENT_ID), USER_ID)).thenReturn(reviews);

        // Act
        final List<Review> result = reviewService.findByEventsAndUser(Collections.singletonList(EVENT_ID), USER_ID);

        // Assert
        assertEquals(reviews, result);
    }

    // ---- create() ----

    // Usuario que puede opinar: no opino todavia, el evento ya paso y estaba confirmado.
    private void eligibleUser() {
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registrationOfTheUser(RegistrationStatus.CONFIRMED)));
    }

    @Test
    public void testCreateSucceedsWhenTheEventFinishedAndTheUserWasRegistered() {
        // Arrange
        eligibleUser();
        when(reviewDao.create(any(Review.class))).thenReturn(new Review(EVENT_ID, USER_ID, RATING, COMMENT));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.CREATED, result);
    }

    @Test
    public void testCreateFailsWhenAlreadyReviewed() {
        // Arrange
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new Review(EVENT_ID, USER_ID, 5, "Ya la mande")));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.ALREADY_REVIEWED, result);
    }

    // Dos pedidos a la vez: el segundo pasa el chequeo de arriba pero la base lo
    // rechaza por la clave unica (evento + usuario).
    @Test
    public void testCreateReturnsAlreadyReviewedWhenTheDatabaseRejectsADuplicate() {
        // Arrange
        eligibleUser();
        when(reviewDao.create(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("unique evento+usuario"));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.ALREADY_REVIEWED, result);
    }

    @Test
    public void testCreateFailsWhenTheEventDoesNotExist() {
        // Arrange
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheEventHasNotFinishedYet() {
        // Arrange
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(futureEvent()));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheUserWasNeverRegistered() {
        // Arrange
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheEventWasCancelled() {
        // Arrange
        final Event cancelled = pastEvent();
        cancelled.setCancelledAt(new Date());
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));
        // Aunque el usuario si estuvo confirmado, un evento cancelado no se puede opinar: el service
        // corta antes de mirar la inscripcion, por eso este stub es lenient (no se usa a proposito).
        lenient().when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registrationOfTheUser(RegistrationStatus.CONFIRMED)));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheUserOnlyReachedTheWaitlist() {
        // Arrange
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registrationOfTheUser(RegistrationStatus.WAITLISTED)));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    // ---- findCreatorAverageRating() ----

    @Test
    public void testFindCreatorAverageRatingReturnsTheAverageTheDaoComputes() {
        // Arrange
        when(reviewDao.findAverageRatingByCreatorId(CREATOR_ID)).thenReturn(4.5);

        // Act
        final double result = reviewService.findCreatorAverageRating(CREATOR_ID);

        // Assert
        assertEquals(4.5, result, 0.0001);
    }

    @Test
    public void testFindCreatorAverageRatingIsZeroWhenTheCreatorHasNoReviews() {
        // Arrange
        when(reviewDao.findAverageRatingByCreatorId(CREATOR_ID)).thenReturn(0.0);

        // Act
        final double result = reviewService.findCreatorAverageRating(CREATOR_ID);

        // Assert
        assertEquals(0.0, result, 0.0001);
    }

    // ---- findByCreatorId() ----

    @Test
    public void testFindByCreatorIdReturnsTheReviewsOfThatCreatorsEvents() {
        // Arrange
        final List<Review> reviews = Arrays.asList(
                new Review(EVENT_ID, USER_ID, 5, "Genial"),
                new Review(EVENT_ID + 1, USER_ID, 3, "Regular"));
        when(reviewDao.findByCreatorId(CREATOR_ID)).thenReturn(reviews);

        // Act
        final List<Review> result = reviewService.findByCreatorId(CREATOR_ID);

        // Assert
        assertEquals(reviews, result);
    }

    @Test
    public void testFindByCreatorIdReturnsEmptyWhenNoReviews() {
        // Arrange
        when(reviewDao.findByCreatorId(CREATOR_ID)).thenReturn(Collections.emptyList());

        // Act
        final List<Review> result = reviewService.findByCreatorId(CREATOR_ID);

        // Assert
        assertEquals(Collections.emptyList(), result);
    }
}
