package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.ReviewDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
    private ReviewDao mockReviewDao;

    @Mock
    private EventService mockEventService;

    @Mock
    private RegistrationService mockRegistrationService;

    @InjectMocks
    private ReviewServiceImpl reviewService;

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

    // ---- findByEventAndUser() ----

    @Test
    public void testFindByEventAndUserReturnsWhatTheDaoReturns() {
        // Arrange
        final Review review = new Review(EVENT_ID, USER_ID, RATING, COMMENT);
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.of(review));

        // Act
        final Optional<Review> result = reviewService.findByEventAndUser(EVENT_ID, USER_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(RATING, result.get().getRating());
    }

    // ---- create() ----

    @Test
    public void testCreateSucceedsWhenTheEventFinishedAndTheUserWasRegistered() {
        // Arrange
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(mockRegistrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new Registration(EVENT_ID, USER_EMAIL, "Ana", RegistrationStatus.CONFIRMED)));
        // create() es void: se captura la reseña persistida con doAnswer() en
        // vez de con Mockito.verify(), para comprobar despues que los datos
        // que le llegaron al dao son los correctos.
        final Review[] created = new Review[1];
        doAnswer(invocation -> {
            created[0] = invocation.getArgument(0);
            return null;
        }).when(mockReviewDao).create(any(Review.class));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.CREATED, result);
        assertEquals(EVENT_ID, created[0].getEventId());
        assertEquals(USER_ID, created[0].getUserId());
        assertEquals(RATING, created[0].getRating());
        assertEquals(COMMENT, created[0].getComment());
    }

    @Test
    public void testCreateFailsWhenAlreadyReviewed() {
        // Arrange
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new Review(EVENT_ID, USER_ID, 5, "Ya la mande")));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.ALREADY_REVIEWED, result);
    }

    @Test
    public void testCreateFailsWhenTheEventDoesNotExist() {
        // Arrange
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheEventHasNotFinishedYet() {
        // Arrange
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent()));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheUserWasNeverRegistered() {
        // Arrange
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(mockRegistrationService.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

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
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheUserOnlyReachedTheWaitlist() {
        // Arrange
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(mockRegistrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new Registration(EVENT_ID, USER_EMAIL, "Ana", RegistrationStatus.WAITLISTED)));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    // ---- findCreatorAverageRating() ----

    @Test
    public void testFindCreatorAverageRatingReturnsWhatTheDaoCalculates() {
        // Arrange
        when(mockReviewDao.findAverageRatingByCreatorId(CREATOR_ID)).thenReturn(4.5);

        // Act
        final double result = reviewService.findCreatorAverageRating(CREATOR_ID);

        // Assert
        assertEquals(4.5, result, 0.0001);
    }

    // ---- findByCreatorId() ----

    @Test
    public void testFindByCreatorIdReturnsWhatTheDaoReturns() {
        // Arrange
        final List<Review> reviews = Arrays.asList(
                new Review(EVENT_ID, USER_ID, 5, "Genial"),
                new Review(EVENT_ID + 1, USER_ID, 3, "Regular"));
        when(mockReviewDao.findByCreatorId(CREATOR_ID)).thenReturn(reviews);

        // Act
        final List<Review> result = reviewService.findByCreatorId(CREATOR_ID);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    public void testFindByCreatorIdReturnsEmptyWhenNoReviews() {
        // Arrange
        when(mockReviewDao.findByCreatorId(CREATOR_ID)).thenReturn(Collections.emptyList());

        // Act
        final List<Review> result = reviewService.findByCreatorId(CREATOR_ID);

        // Assert
        assertTrue(result.isEmpty());
    }
}
