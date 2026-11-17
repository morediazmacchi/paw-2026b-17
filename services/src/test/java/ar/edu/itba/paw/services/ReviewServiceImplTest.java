package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.RatingSummary;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.ReviewDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
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
import static org.mockito.Mockito.lenient;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: ReviewDao, EventDao y UserService son @Mock
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
    private EventService eventService;
    @Mock
    private UserService userService;

    private ReviewServiceImpl reviewService;

    @Before
    public void setUp() {
        reviewService = new ReviewServiceImpl(reviewDao, eventService, userService);
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
        event.setCreator(ModelFixtures.user(CREATOR_ID));
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
    public void testFindByEventAndUserReturnsTheReviewTheUserLeftForThatEvent() {
        // Arrange
        final Review review = new Review(ModelFixtures.event(EVENT_ID), ModelFixtures.user(USER_ID), RATING, COMMENT);
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

    // ---- create() ----

    // Usuario que puede opinar: no opino todavia y el evento ya paso.
    private void eligibleUser() {
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(someUser()));
    }

    @Test
    public void testCreateSucceedsWhenTheEventFinishedAndTheUserWasRegistered() {
        // Arrange
        eligibleUser();
        when(reviewDao.create(any(Review.class))).thenReturn(new Review(ModelFixtures.event(EVENT_ID), ModelFixtures.user(USER_ID), RATING, COMMENT));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.CREATED, result);
    }

    @Test
    public void testCreateFailsWhenAlreadyReviewed() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new Review(ModelFixtures.event(EVENT_ID), ModelFixtures.user(USER_ID), 5, "Ya la mande")));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.ALREADY_REVIEWED, result);
    }

    // Dos pedidos a la vez: el segundo pasa el chequeo de arriba pero la base lo
    // rechaza por la clave unica (evento + usuario): SQLSTATE 23505.
    @Test
    public void testCreateReturnsAlreadyReviewedWhenTheDatabaseRejectsADuplicate() {
        // Arrange
        eligibleUser();
        when(reviewDao.create(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("unique evento+usuario",
                        new SQLException("duplicado", "23505")));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.ALREADY_REVIEWED, result);
    }

    @Test
    public void testCreateRethrowsADatabaseViolationThatIsNotTheDuplicate() {
        // Arrange: un CHECK (SQLSTATE 23514) no es "ya la mando": no se disfraza de ALREADY_REVIEWED
        eligibleUser();
        when(reviewDao.create(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("check", new SQLException("check", "23514")));

        // Act + Assert
        assertThrows(DataIntegrityViolationException.class,
                () -> reviewService.create(EVENT_ID, USER_ID, RATING, COMMENT));
    }

    @Test
    public void testCreateWithARatingAboveTheMaximumReturnsInvalid() {
        // Arrange: usuario elegible; si el service guardara la resenia igual, el resultado seria CREATED
        lenient().when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        lenient().when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pastEvent()));
        lenient().when(userService.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        lenient().when(reviewDao.create(any(Review.class))).thenReturn(reviewOfEvent(EVENT_ID));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, ReviewService.MAX_RATING + 1, COMMENT);

        // Assert
        assertEquals(ReviewResult.INVALID, result);
    }

    @Test
    public void testCreateWithANegativeRatingReturnsInvalid() {
        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, ReviewService.MIN_RATING - 1, COMMENT);

        // Assert
        assertEquals(ReviewResult.INVALID, result);
    }

    @Test
    public void testCreateWithACommentLongerThanTheMaximumReturnsInvalid() {
        // Arrange
        final String tooLong = new String(new char[ReviewService.MAX_COMMENT_LENGTH + 1]).replace('\0', 'a');

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, RATING, tooLong);

        // Assert
        assertEquals(ReviewResult.INVALID, result);
    }

    @Test
    public void testCreateFailsWhenTheEventDoesNotExist() {
        // Arrange
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheEventHasNotFinishedYet() {
        // Arrange
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(futureEvent()));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    @Test
    public void testCreateFailsWhenTheEventWasCancelled() {
        // Arrange
        final Event cancelled = pastEvent();
        cancelled.setCancelledAt(new Date());
        when(reviewDao.findByEventAndUser(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));
        // Un evento cancelado no se puede opinar: el service corta antes de cargar la cuenta, por eso este stub
        // es lenient (no se usa a proposito).
        lenient().when(userService.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        // Act
        final ReviewResult result = reviewService.create(EVENT_ID, USER_ID, RATING, COMMENT);

        // Assert
        assertEquals(ReviewResult.NOT_ELIGIBLE, result);
    }

    // ---- findCreatorReputation() ----

    private static Review reviewOfEvent(final long eventId) {
        return new Review(ModelFixtures.event(eventId), someUser(), RATING, COMMENT);
    }

    @Test
    public void testFindCreatorReputationWithoutReviewsIsEmpty() {
        // Arrange
        when(reviewDao.findRatingSummaryByCreatorId(CREATOR_ID)).thenReturn(new RatingSummary(0L, 0.0));

        // Act
        final CreatorReputation result = reviewService.findCreatorReputation(CREATOR_ID, 1);

        // Assert
        assertFalse(result.hasReviews());
        assertEquals(0, result.getReviewCount());
        assertEquals(0.0, result.getAverageRating(), 0.0001);
        assertTrue(result.getEventsById().isEmpty());
    }

    @Test
    public void testFindCreatorReputationReturnsThePageTheTotalAndTheAverage() {
        // Arrange: dos reseñas mas que una pagina, o sea 2 paginas; se pide la primera.
        final long total = ReviewServiceImpl.REVIEWS_PAGE_SIZE + 2;
        final List<Review> firstPage = Arrays.asList(reviewOfEvent(1), reviewOfEvent(2));
        when(reviewDao.findRatingSummaryByCreatorId(CREATOR_ID)).thenReturn(new RatingSummary(total, 4.5));
        when(reviewDao.findByCreatorId(CREATOR_ID, 1, ReviewServiceImpl.REVIEWS_PAGE_SIZE)).thenReturn(firstPage);
        when(eventService.findByIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(ModelFixtures.event(1), ModelFixtures.event(2)));

        // Act
        final CreatorReputation result = reviewService.findCreatorReputation(CREATOR_ID, 1);

        // Assert
        assertTrue(result.hasReviews());
        assertEquals(total, result.getReviewCount());
        assertEquals(firstPage, result.getReviews());
        assertEquals(1, result.getPage());
        assertEquals(2, result.getTotalPages());
        assertEquals(4.5, result.getAverageRating(), 0.0001);
    }

    @Test
    public void testFindCreatorReputationWithAPageOutOfRangeReturnsTheLastOne() {
        // Arrange: 2 paginas en total. Pedir la 9 tiene que pedirle al dao la 2 (si pidiera otra,
        // el stub no coincide y el runner estricto hace fallar el test).
        when(reviewDao.findRatingSummaryByCreatorId(CREATOR_ID))
                .thenReturn(new RatingSummary((long) ReviewServiceImpl.REVIEWS_PAGE_SIZE + 2, 4.0));
        when(reviewDao.findByCreatorId(CREATOR_ID, 2, ReviewServiceImpl.REVIEWS_PAGE_SIZE))
                .thenReturn(Arrays.asList(reviewOfEvent(1), reviewOfEvent(2)));
        when(eventService.findByIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(ModelFixtures.event(1), ModelFixtures.event(2)));

        // Act
        final CreatorReputation result = reviewService.findCreatorReputation(CREATOR_ID, 9);

        // Assert
        assertEquals(2, result.getPage());
    }

    @Test
    public void testFindCreatorReputationAsksOnceForEachReviewedEventAndIndexesThemById() {
        // Arrange: dos reseñas del mismo evento y una de otro. El dao solo responde si se le piden
        // los ids sin repetir (1 y 2), asi que si el service pidiera de mas, el mapa quedaria vacio.
        when(reviewDao.findRatingSummaryByCreatorId(CREATOR_ID)).thenReturn(new RatingSummary(3L, 4.0));
        when(reviewDao.findByCreatorId(CREATOR_ID, 1, ReviewServiceImpl.REVIEWS_PAGE_SIZE))
                .thenReturn(Arrays.asList(reviewOfEvent(1), reviewOfEvent(2), reviewOfEvent(1)));
        final Event first = ModelFixtures.event(1);
        final Event second = ModelFixtures.event(2);
        when(eventService.findByIds(Arrays.asList(1L, 2L))).thenReturn(Arrays.asList(first, second));

        // Act
        final CreatorReputation result = reviewService.findCreatorReputation(CREATOR_ID, 1);

        // Assert
        assertEquals(2, result.getEventsById().size());
        assertSame(first, result.getEventsById().get(1L));
        assertSame(second, result.getEventsById().get(2L));
    }

    // ---- findByEventsAndUser() / findRatingSummaryByCreator() ----

    @Test
    public void testFindByEventsAndUserReturnsTheReviewsOfThatPersonInThoseEvents() {
        // Arrange: el stub solo responde con esos eventos y esa persona
        final Review mine = new Review(ModelFixtures.event(7L), ModelFixtures.user(USER_ID), 4, "Muy bueno");
        when(reviewDao.findByEventsAndUser(Arrays.asList(7L, 8L), USER_ID)).thenReturn(Collections.singletonList(mine));

        // Act
        final List<Review> result = reviewService.findByEventsAndUser(Arrays.asList(7L, 8L), USER_ID);

        // Assert
        assertEquals(Collections.singletonList(mine), result);
    }

    @Test
    public void testFindRatingSummaryByCreatorIsTheCountAndTheAverageOfThatOrganizer() {
        // Arrange
        when(reviewDao.findRatingSummaryByCreatorId(CREATOR_ID)).thenReturn(new RatingSummary(6L, 4.5));

        // Act
        final RatingSummary result = reviewService.findRatingSummaryByCreator(CREATOR_ID);

        // Assert
        assertEquals(6L, result.getCount());
        assertEquals(4.5, result.getAverage(), 0.0001);
    }
}
