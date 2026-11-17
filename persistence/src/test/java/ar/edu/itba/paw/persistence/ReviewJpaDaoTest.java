package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.RatingSummary;
import ar.edu.itba.paw.model.Review;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class ReviewJpaDaoTest {

    @PersistenceContext
    private EntityManager em;

    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Autowired
    private DataSource ds;

    @Autowired
    private ReviewDao reviewDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;
    private long creatorId;
    private long eventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = insertUser("asistente@mail.com", "Ana");
        creatorId = insertUser(CREATOR_EMAIL, "Creador");
        eventId = insertEvent("Recital", CREATOR_EMAIL, creatorId);
    }

    private long insertUser(final String email, final String name) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, name);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    // El evento no necesita categoria para estos tests (son de reseñas, no de
    // categorias): sin la columna category_id, una fila de event ya no
    // depende de que exista una. creatorId null (el evento "no relacionado" de
    // los tests de abajo) es igual de valido: la columna es nullable.
    private long insertEvent(final String title, final String creatorEmail, final Long creatorId) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, creator_id) VALUES (?, ?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", creatorEmail, creatorId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM event WHERE title = ? AND creator_email = ?", Long.class, title, creatorEmail);
    }

    private long insertReview(final long eventId, final long userId, final int rating, final String comment) {
        jdbcTemplate.update(
                "INSERT INTO review (event_id, user_id, rating, comment) VALUES (?, ?, ?, ?)",
                eventId, userId, rating, comment);
        return jdbcTemplate.queryForObject("SELECT id FROM review ORDER BY id DESC LIMIT 1", Long.class);
    }

    // ---- create() ----

    @Test
    public void testCreateInsertsTheReviewRowAndReturnsItWithItsId() {
        // Act
        final Review created = reviewDao.create(eventId, userId, 4, "Muy bueno");

        // Assert
        assertTrue(created.getId() > 0);
        final java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, user_id, rating, comment FROM review WHERE id = ?", created.getId());
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertEquals(userId, ((Number) row.get("user_id")).longValue());
        assertEquals(4, row.get("rating"));
        assertEquals("Muy bueno", row.get("comment"));
    }

    @Test
    public void testCreateAllowsZeroRating() {
        // "de 0 a 5 estrellas": 0 es un valor valido, no "sin calificar".

        // Act
        final Review created = reviewDao.create(eventId, userId, 0, "Malisimo");

        // Assert
        assertTrue(created.getId() > 0);
        final Integer rating = jdbcTemplate.queryForObject(
                "SELECT rating FROM review WHERE id = ?", Integer.class, created.getId());
        assertEquals(Integer.valueOf(0), rating);
    }

    @Test(expected = DataAccessException.class)
    public void testCreateTwiceForTheSameEventAndUserThrowsDataAccessException() {
        // Arrange
        // UNIQUE(event_id, user_id): una sola reseña por persona y evento.
        insertReview(eventId, userId, 5, "Genial");

        // Act
        reviewDao.create(eventId, userId, 1, "Cambie de opinion");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findByEventAndUser() ----

    @Test
    public void testFindByEventAndUserReturnsTheStoredReview() {
        // Arrange
        insertReview(eventId, userId, 4, "Muy bueno");

        // Act
        final Optional<Review> found = reviewDao.findByEventAndUser(eventId, userId);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(4, found.get().getRating());
        assertEquals("Muy bueno", found.get().getComment());
    }

    @Test
    public void testFindByEventAndUserReturnsEmptyWhenNeverReviewed() {
        // Arrange: no se inserta ninguna reseña.

        // Act
        final Optional<Review> found = reviewDao.findByEventAndUser(eventId, userId);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- findByEventsAndUser() ----

    @Test
    public void testFindByEventsAndUserReturnsOnlyThatUsersReviewsOfThoseEvents() {
        // Arrange
        final long otherEventId = insertEvent("Torneo", CREATOR_EMAIL, creatorId);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertReview(eventId, userId, 4, "Muy bueno");
        insertReview(otherEventId, otherUserId, 2, "No era mio");

        // Act
        final List<Review> found = reviewDao.findByEventsAndUser(Arrays.asList(eventId, otherEventId), userId);

        // Assert
        assertEquals(1, found.size());
        assertEquals(eventId, found.get(0).getEventId());
    }

    @Test
    public void testFindByEventsAndUserReturnsEmptyWhenTheListOfEventsIsEmpty() {
        // Arrange
        insertReview(eventId, userId, 4, "Muy bueno");

        // Act
        final List<Review> found = reviewDao.findByEventsAndUser(Collections.emptyList(), userId);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- findByCreatorId() ----

    // Como insertReview(), pero con la fecha elegida: para los tests que miran el orden.
    private long insertReviewAt(final long eventId, final long userId, final int rating, final Timestamp createdAt) {
        jdbcTemplate.update(
                "INSERT INTO review (event_id, user_id, rating, comment, created_at) VALUES (?, ?, ?, ?, ?)",
                eventId, userId, rating, null, createdAt);
        return jdbcTemplate.queryForObject("SELECT id FROM review ORDER BY id DESC LIMIT 1", Long.class);
    }

    @Test
    public void testFindByCreatorIdReturnsReviewsAcrossAllTheirEvents() {
        // Arrange
        final long otherEventId = insertEvent("Torneo", CREATOR_EMAIL, creatorId);
        final long unrelatedEventId = insertEvent("Muestra", "otro@mail.com", null);
        insertReview(eventId, userId, 5, "Buenisimo");
        insertReview(otherEventId, userId, 3, "Regular");
        insertReview(unrelatedEventId, userId, 1, "No cuenta");

        // Act
        final List<Review> found = reviewDao.findByCreatorId(creatorId, 1, 10);

        // Assert
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(r -> r.getEventId() == eventId));
        assertTrue(found.stream().anyMatch(r -> r.getEventId() == otherEventId));
    }

    @Test
    public void testFindByCreatorIdReturnsTheNewestFirst() {
        // Arrange: dos reseñas del mismo creador, la segunda un minuto mas nueva.
        final long otherEventId = insertEvent("Torneo", CREATOR_EMAIL, creatorId);
        final long now = System.currentTimeMillis();
        final long older = insertReviewAt(eventId, userId, 3, new Timestamp(now - 60_000));
        final long newer = insertReviewAt(otherEventId, userId, 5, new Timestamp(now));

        // Act
        final List<Review> found = reviewDao.findByCreatorId(creatorId, 1, 10);

        // Assert
        assertEquals(newer, found.get(0).getId().longValue());
        assertEquals(older, found.get(1).getId().longValue());
    }

    @Test
    public void testFindByCreatorIdReturnsOnlyThePageAsked() {
        // Arrange: 3 reseñas del creador. De a 2, la pagina 2 tiene solo la mas vieja.
        final long secondEventId = insertEvent("Torneo", CREATOR_EMAIL, creatorId);
        final long thirdEventId = insertEvent("Feria", CREATOR_EMAIL, creatorId);
        final long now = System.currentTimeMillis();
        final long oldest = insertReviewAt(eventId, userId, 3, new Timestamp(now - 120_000));
        insertReviewAt(secondEventId, userId, 4, new Timestamp(now - 60_000));
        insertReviewAt(thirdEventId, userId, 5, new Timestamp(now));

        // Act
        final List<Review> page = reviewDao.findByCreatorId(creatorId, 2, 2);

        // Assert
        assertEquals(1, page.size());
        assertEquals(oldest, page.get(0).getId().longValue());
    }

    @Test
    public void testFindByCreatorIdReturnsEmptyWhenNoReviews() {
        // Arrange: no se inserta ninguna reseña.

        // Act
        final List<Review> found = reviewDao.findByCreatorId(creatorId, 1, 10);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- findRatingSummaryByCreatorId() ----

    @Test
    public void testRatingSummaryCountsOnlyTheReviewsOfThatCreatorsEvents() {
        // Arrange
        final long otherEventId = insertEvent("Torneo", CREATOR_EMAIL, creatorId);
        final long unrelatedEventId = insertEvent("Muestra", "otro@mail.com", null);
        insertReview(eventId, userId, 5, "Buenisimo");
        insertReview(otherEventId, userId, 3, "Regular");
        insertReview(unrelatedEventId, userId, 1, "No cuenta");

        // Act
        final RatingSummary summary = reviewDao.findRatingSummaryByCreatorId(creatorId);

        // Assert
        assertEquals(2, summary.getCount());
    }

    @Test
    public void testRatingSummaryAveragesAllTheCreatorsEventsAndIgnoresOthers() {
        // Arrange: 5 y 4 dan 4,5 (y no 4, como un AVG entero); la de otro organizador (1 estrella) no entra
        final long otherEventId = insertEvent("Torneo", CREATOR_EMAIL, creatorId);
        final long unrelatedEventId = insertEvent("Muestra", "otro@mail.com", null);
        insertReview(eventId, userId, 5, "Buenisimo");
        insertReview(otherEventId, userId, 4, "Bien");
        insertReview(unrelatedEventId, userId, 1, "No cuenta");

        // Act
        final RatingSummary summary = reviewDao.findRatingSummaryByCreatorId(creatorId);

        // Assert
        assertEquals(4.5, summary.getAverage(), 0.0001);
    }

    @Test
    public void testRatingSummaryWithoutReviewsIsZeroCountAndZeroAverage() {
        // Arrange: no se inserta ninguna reseña.

        // Act
        final RatingSummary summary = reviewDao.findRatingSummaryByCreatorId(creatorId);

        // Assert
        assertEquals(0, summary.getCount());
        assertEquals(0, summary.getAverage(), 0.0001);
        assertFalse(summary.hasReviews());
    }
}
