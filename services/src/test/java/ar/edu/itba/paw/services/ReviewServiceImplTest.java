package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReviewServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long USER_ID = 2;
    private static final String CREATOR_EMAIL = "creador@mail.com";
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
        final Review review = new Review(EVENT_ID, USER_ID, RATING, COMMENT);
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.of(review));

        final Optional<Review> result = reviewService.findByEventAndUser(EVENT_ID, USER_ID);

        assertTrue(result.isPresent());
        assertEquals(RATING, result.get().getRating());
    }

    // ---- create() ----

    @Test
    public void testCreateSucceedsWhenTheEventFinishedAndTheUserWasRegistered() {
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(mockRegistrationService.findByEventAndEmail(EVENT_ID, USER_EMAIL))
                .thenReturn(Optional.of(new Registration(EVENT_ID, USER_EMAIL, "Ana", Registration.STATUS_CONFIRMED)));

        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        assertEquals(ReviewResult.CREATED, result);
        verify(mockReviewDao).create(any(Review.class));
    }

    @Test
    public void testCreateFailsWhenAlreadyReviewed() {
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new Review(EVENT_ID, USER_ID, 5, "Ya la mande")));

        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        assertEquals(ReviewResult.ALREADY_REVIEWED, result);
        verify(mockReviewDao, never()).create(any(Review.class));
    }

    @Test
    public void testCreateFailsWhenTheEventDoesNotExist() {
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
        verify(mockReviewDao, never()).create(any(Review.class));
    }

    @Test
    public void testCreateFailsWhenTheEventHasNotFinishedYet() {
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(futureEvent()));

        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
        verify(mockReviewDao, never()).create(any(Review.class));
    }

    @Test
    public void testCreateFailsWhenTheUserWasNeverRegistered() {
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(mockRegistrationService.findByEventAndEmail(EVENT_ID, USER_EMAIL)).thenReturn(Optional.empty());

        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
        verify(mockReviewDao, never()).create(any(Review.class));
    }

    @Test
    public void testCreateFailsWhenTheEventWasCancelled() {
        final Event cancelled = pastEvent();
        cancelled.setCancelledAt(new Date());
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(cancelled));

        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
        verify(mockReviewDao, never()).create(any(Review.class));
    }

    @Test
    public void testCreateFailsWhenTheUserOnlyReachedTheWaitlist() {
        when(mockReviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(mockRegistrationService.findByEventAndEmail(EVENT_ID, USER_EMAIL))
                .thenReturn(Optional.of(new Registration(EVENT_ID, USER_EMAIL, "Ana", Registration.STATUS_WAITLISTED)));

        final ReviewResult result = reviewService.create(EVENT_ID, someUser(), RATING, COMMENT);

        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
        verify(mockReviewDao, never()).create(any(Review.class));
    }

    // ---- findCreatorAverageRating() ----

    @Test
    public void testFindCreatorAverageRatingReturnsWhatTheDaoCalculates() {
        when(mockReviewDao.findAverageRatingByCreatorEmail(CREATOR_EMAIL)).thenReturn(4.5);

        assertEquals(4.5, reviewService.findCreatorAverageRating(CREATOR_EMAIL), 0.0001);
    }

    // ---- findByCreatorEmail() ----

    @Test
    public void testFindByCreatorEmailReturnsWhatTheDaoReturns() {
        final List<Review> reviews = Arrays.asList(
                new Review(EVENT_ID, USER_ID, 5, "Genial"),
                new Review(EVENT_ID + 1, USER_ID, 3, "Regular"));
        when(mockReviewDao.findByCreatorEmail(CREATOR_EMAIL)).thenReturn(reviews);

        final List<Review> result = reviewService.findByCreatorEmail(CREATOR_EMAIL);

        assertEquals(2, result.size());
    }

    @Test
    public void testFindByCreatorEmailReturnsEmptyWhenNoReviews() {
        when(mockReviewDao.findByCreatorEmail(CREATOR_EMAIL)).thenReturn(Collections.emptyList());

        final List<Review> result = reviewService.findByCreatorEmail(CREATOR_EMAIL);

        assertTrue(result.isEmpty());
    }
}
