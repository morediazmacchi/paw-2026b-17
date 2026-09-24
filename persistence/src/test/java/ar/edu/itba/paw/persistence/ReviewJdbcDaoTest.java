package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
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

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class ReviewJdbcDaoTest {

    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Autowired
    private DataSource ds;

    @Autowired
    private ReviewDao reviewDao;

    @Autowired
    private UserDao userDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;
    private long eventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = userDao.create(new User("asistente@mail.com", "Ana")).getId();
        eventId = insertEvent("Recital", CREATOR_EMAIL);
    }

    // El evento no necesita categoria para estos tests (son de reseñas, no de
    // categorias): sin la columna category_id, una fila de event ya no
    // depende de que exista una.
    private long insertEvent(final String title, final String creatorEmail) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", creatorEmail);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM event WHERE title = ? AND creator_email = ?", Long.class, title, creatorEmail);
    }

    // ---- create() + findByEventAndUser() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final Review created = reviewDao.create(new Review(eventId, userId, 5, "Buenisimo"));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByEventAndUserReturnTheSameReview() {
        reviewDao.create(new Review(eventId, userId, 4, "Muy bueno"));

        final Optional<Review> found = reviewDao.findByEventAndUser(eventId, userId);

        assertTrue(found.isPresent());
        assertEquals(4, found.get().getRating());
        assertEquals("Muy bueno", found.get().getComment());
    }

    @Test
    public void testFindByEventAndUserReturnsEmptyWhenNeverReviewed() {
        final Optional<Review> found = reviewDao.findByEventAndUser(eventId, userId);

        assertFalse(found.isPresent());
    }

    @Test(expected = DataAccessException.class)
    public void testCreateTwiceForTheSameEventAndUserThrowsDataAccessException() {
        // UNIQUE(event_id, user_id): una sola reseña por persona y evento.
        reviewDao.create(new Review(eventId, userId, 5, "Genial"));
        reviewDao.create(new Review(eventId, userId, 1, "Cambie de opinion"));
    }

    @Test
    public void testCreateAllowsZeroRating() {
        // "de 0 a 5 estrellas": 0 es un valor valido, no "sin calificar".
        final Review created = reviewDao.create(new Review(eventId, userId, 0, "Malisimo"));

        assertEquals(0, reviewDao.findByEventAndUser(eventId, userId).get().getRating());
        assertTrue(created.getId() > 0);
    }

    // ---- findByCreatorEmail() ----

    @Test
    public void testFindByCreatorEmailReturnsReviewsAcrossAllTheirEvents() {
        final long otherEventId = insertEvent("Torneo", CREATOR_EMAIL);
        final long unrelatedEventId = insertEvent("Muestra", "otro@mail.com");
        reviewDao.create(new Review(eventId, userId, 5, "Buenisimo"));
        reviewDao.create(new Review(otherEventId, userId, 3, "Regular"));
        reviewDao.create(new Review(unrelatedEventId, userId, 1, "No cuenta"));

        final List<Review> found = reviewDao.findByCreatorEmail(CREATOR_EMAIL);

        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(r -> r.getEventId() == eventId));
        assertTrue(found.stream().anyMatch(r -> r.getEventId() == otherEventId));
    }

    @Test
    public void testFindByCreatorEmailReturnsEmptyWhenNoReviews() {
        assertTrue(reviewDao.findByCreatorEmail(CREATOR_EMAIL).isEmpty());
    }

    // ---- findAverageRatingByCreatorEmail() ----

    @Test
    public void testAverageRatingJoinsAllTheCreatorsEventsAndIgnoresOthers() {
        final long otherEventId = insertEvent("Torneo", CREATOR_EMAIL);
        final long unrelatedEventId = insertEvent("Muestra", "otro@mail.com");
        reviewDao.create(new Review(eventId, userId, 5, "Buenisimo"));
        reviewDao.create(new Review(otherEventId, userId, 4, "Bien"));
        reviewDao.create(new Review(unrelatedEventId, userId, 1, "No cuenta"));

        assertEquals(4.5, reviewDao.findAverageRatingByCreatorEmail(CREATOR_EMAIL), 0.0001);
    }

    @Test
    public void testAverageRatingIsZeroWhenNoReviews() {
        assertEquals(0, reviewDao.findAverageRatingByCreatorEmail(CREATOR_EMAIL), 0.0001);
    }
}
